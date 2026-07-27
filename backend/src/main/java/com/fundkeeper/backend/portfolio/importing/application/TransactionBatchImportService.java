package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fundkeeper.backend.account.application.AccountNameNormalizer;
import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.portfolio.application.BuyTransactionCommand;
import com.fundkeeper.backend.portfolio.application.BuyTransactionOutcome;
import com.fundkeeper.backend.portfolio.application.BuyTransactionPlan;
import com.fundkeeper.backend.portfolio.application.BuyTransactionPlanner;
import com.fundkeeper.backend.portfolio.application.PortfolioService;
import com.fundkeeper.backend.portfolio.application.SellTransactionCommand;
import com.fundkeeper.backend.portfolio.application.SellTransactionOutcome;
import com.fundkeeper.backend.portfolio.application.SellTransactionPlan;
import com.fundkeeper.backend.portfolio.application.SellTransactionPlanner;
import com.fundkeeper.backend.portfolio.application.SellTransactionService;
import com.fundkeeper.backend.fund.application.FundCatalogService;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.SellMode;
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.importing.application.TransactionBatchDocument.TransactionRowDocument;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatch;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchRepository;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;
import com.fundkeeper.backend.portfolio.importing.domain.ImportIssueSeverity;
import com.fundkeeper.backend.portfolio.importing.domain.TransactionImportAction;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class TransactionBatchImportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String IMPORT_TYPE = "TRANSACTION_BATCH";
    private static final int MAX_TRANSACTIONS = 500;
    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final AccountNameNormalizer accountNameNormalizer;
    private final SnapshotBoundaryRepository boundaryRepository;
    private final PortfolioRepository portfolioRepository;
    private final ImportBatchRepository batchRepository;
    private final BuyTransactionPlanner buyPlanner;
    private final PortfolioService portfolioService;
    private final FundCatalogService fundCatalogService;
    private final SellTransactionPlanner sellPlanner;
    private final SellTransactionService sellTransactionService;
    private final ImportFingerprint fingerprint;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public TransactionBatchImportService(
            ObjectMapper objectMapper,
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            AccountNameNormalizer accountNameNormalizer,
            SnapshotBoundaryRepository boundaryRepository,
            PortfolioRepository portfolioRepository,
            ImportBatchRepository batchRepository,
            BuyTransactionPlanner buyPlanner,
            PortfolioService portfolioService,
            FundCatalogService fundCatalogService,
            SellTransactionPlanner sellPlanner,
            SellTransactionService sellTransactionService,
            ImportFingerprint fingerprint,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountNameNormalizer = accountNameNormalizer;
        this.boundaryRepository = boundaryRepository;
        this.portfolioRepository = portfolioRepository;
        this.batchRepository = batchRepository;
        this.buyPlanner = buyPlanner;
        this.portfolioService = portfolioService;
        this.fundCatalogService = fundCatalogService;
        this.sellPlanner = sellPlanner;
        this.sellTransactionService = sellTransactionService;
        this.fingerprint = fingerprint;
        this.clock = clock;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TransactionBatchPreflightResult preflight(
            String userPublicId,
            String rawJson) {
        User user = activeUserForUpdate(userPublicId);
        ParsedDocument parsed = parse(rawJson);
        ImportBatch existing = validBatchId(parsed.batchId())
                ? batchRepository.findByUserIdAndBatchId(
                                user.id(),
                                parsed.batchId())
                        .orElse(null)
                : null;

        if (existing != null
                && (!IMPORT_TYPE.equals(existing.importType())
                        || existing.status()
                                == ImportBatchStatus.COMMITTED)) {
            if (parsed.document() != null
                    && IMPORT_TYPE.equals(existing.importType())
                    && Objects.equals(
                            existing.contentHash(),
                            contentHash(parsed.document()))) {
                return readPreflight(existing.preflightJson())
                        .withStatus(ImportBatchStatus.COMMITTED);
            }
            return batchConflict(parsed);
        }

        if (parsed.document() == null) {
            TransactionBatchPreflightResult failed =
                    schemaFailure(parsed);
            if (validBatchId(parsed.batchId())) {
                savePreflight(
                        user,
                        parsed,
                        existing,
                        null,
                        failed,
                        null);
            }
            return failed;
        }

        List<ImportIssue> schemaIssues =
                validateSchema(parsed.document());
        String contentHash = contentHash(parsed.document());
        if (!schemaIssues.isEmpty()) {
            TransactionBatchPreflightResult failed =
                    schemaFailure(
                            parsed.document(),
                            schemaIssues);
            if (validBatchId(parsed.document().batchId())) {
                savePreflight(
                        user,
                        parsed.withContentHash(contentHash),
                        existing,
                        null,
                        failed,
                        null);
            }
            return failed;
        }

        Analysis analysis = analyze(user, parsed.document());
        savePreflight(
                user,
                parsed.withContentHash(contentHash),
                existing,
                analysis.account(),
                analysis.result(),
                analysis.planHash());
        return analysis.result();
    }

    public TransactionBatchCommitOutcome commit(
            String userPublicId,
            String batchId) {
        try {
            TransactionBatchCommitOutcome outcome =
                    transactionTemplate.execute(status ->
                            commitInTransaction(
                                    userPublicId,
                                    batchId));
            if (outcome == null) {
                throw new IllegalStateException(
                        "Import transaction returned no outcome");
            }
            return outcome;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status ->
                    userRepository.findByPublicId(userPublicId)
                            .ifPresent(user ->
                                    batchRepository.markCommitFailed(
                                            user.id(),
                                            batchId,
                                            clock.instant())));
            throw exception;
        }
    }

    private TransactionBatchCommitOutcome commitInTransaction(
            String userPublicId,
            String batchId) {
        User user = activeUserForUpdate(userPublicId);
        ImportBatch batch = batchRepository
                .findByUserIdAndBatchIdForUpdate(user.id(), batchId)
                .filter(value ->
                        IMPORT_TYPE.equals(value.importType()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.IMPORT_BATCH_NOT_FOUND,
                        "交易导入批次不存在"));
        if (batch.status() == ImportBatchStatus.COMMITTED) {
            return new TransactionBatchCommitOutcome(
                    readCommit(batch.commitResultJson()),
                    true);
        }
        if (batch.status() != ImportBatchStatus.READY_TO_COMMIT) {
            throw new BusinessException(
                    ErrorCode.IMPORT_NOT_READY,
                    "交易导入批次尚未通过预检");
        }

        TransactionBatchDocument document =
                readDocument(batch.requestJson());
        Analysis analysis = analyze(user, document);
        if (analysis.result().status()
                        != ImportBatchStatus.READY_TO_COMMIT
                || !Objects.equals(
                        analysis.planHash(),
                        batch.planHash())) {
            throw new BusinessException(
                    ErrorCode.IMPORT_NOT_READY,
                    "账户、行情或交易边界已变化，请重新预检");
        }

        boolean accountCreated = analysis.account().existing() == null;
        FundAccount account = accountCreated
                ? createAccount(user.id(), analysis.account())
                : analysis.account().existing();
        List<TransactionBatchCommitResult.CommittedTransactionRow> rows =
                new ArrayList<>();
        for (TransactionPlan value : analysis.plans()) {
            String transactionPublicId;
            TransactionStatus transactionStatus;
            try {
                if (value.buyPlan() != null) {
                    BuyTransactionCommand previewCommand =
                            value.buyPlan().command();
                    BuyTransactionOutcome outcome =
                            portfolioService.buy(
                                    user.publicId(),
                                    new BuyTransactionCommand(
                                            requestId(
                                                    batch,
                                                    value.row()),
                                            account.publicId(),
                                            previewCommand.fundCode(),
                                            previewCommand.amount(),
                                            previewCommand.submittedDate(),
                                            previewCommand.submittedPeriod(),
                                            previewCommand.confirmedShares(),
                                            previewCommand.confirmedDate(),
                                            previewCommand.note()));
                    transactionPublicId = outcome.details()
                            .transaction()
                            .publicId();
                    transactionStatus = outcome.details()
                            .transaction()
                            .status();
                } else {
                    SellTransactionCommand previewCommand =
                            value.sellPlan().command();
                    SellTransactionOutcome outcome =
                            sellTransactionService.sell(
                                    user.publicId(),
                                    new SellTransactionCommand(
                                            requestId(
                                                    batch,
                                                    value.row()),
                                            account.publicId(),
                                            previewCommand.fundCode(),
                                            previewCommand.sellMode(),
                                            previewCommand.expectedAmount(),
                                            previewCommand
                                                    .actualReceivedAmount(),
                                            previewCommand.submittedDate(),
                                            previewCommand.submittedPeriod(),
                                            previewCommand.confirmedShares(),
                                            previewCommand.confirmedDate(),
                                            previewCommand.note()));
                    transactionPublicId = outcome.details()
                            .transaction()
                            .publicId();
                    transactionStatus = outcome.details()
                            .transaction()
                            .status();
                }
            } catch (BusinessException exception) {
                throw new IllegalStateException(
                        "A preflighted transaction could not be applied",
                        exception);
            }
            rows.add(
                    new TransactionBatchCommitResult
                            .CommittedTransactionRow(
                                    value.row(),
                                    value.source().rowId(),
                                    value.source().fundCode(),
                                    value.source().type(),
                                    transactionPublicId,
                                    transactionStatus));
        }

        Instant now = clock.instant();
        TransactionBatchCommitResult result =
                new TransactionBatchCommitResult(
                        batch.batchId(),
                        ImportBatchStatus.COMMITTED,
                        account.publicId(),
                        accountCreated,
                        rows.size(),
                        List.copyOf(rows),
                        now);
        batchRepository.markCommitted(
                batch.id(),
                account.id(),
                write(result),
                now);
        return new TransactionBatchCommitOutcome(
                result,
                false);
    }

    private Analysis analyze(
            User user,
            TransactionBatchDocument document) {
        List<ImportIssue> issues = new ArrayList<>();
        AccountMatch account =
                matchAccount(user.id(), document, issues);
        Set<String> seenRowIds = new HashSet<>();
        Set<Long> batchOpenSells = new HashSet<>();
        Map<Long, FundPosition> projectedPositions =
                new HashMap<>();
        if (account.existing() != null) {
            portfolioRepository
                    .findPositionsByUserIdAndAccountId(
                            user.id(),
                            account.existing().id())
                    .forEach(position -> projectedPositions.put(
                            position.fundId(),
                            position));
        }
        List<TransactionPlan> plans = new ArrayList<>();
        List<TransactionBatchRowPreview> previews =
                new ArrayList<>();

        for (int index = 0;
                index < document.transactions().size();
                index++) {
            int row = index + 1;
            TransactionRowDocument source =
                    document.transactions().get(index);
            List<ImportIssue> rowIssues = new ArrayList<>();
            if (!seenRowIds.add(source.rowId())) {
                rowIssues.add(ImportIssue.error(
                        row,
                        field(index, "rowId"),
                        "DUPLICATE_ROW_ID",
                        "同一批次内 rowId 不能重复"));
            }
            BuyTransactionPlan buyPlan = null;
            SellTransactionPlan sellPlan = null;
            if ("BUY".equals(source.type())) {
                try {
                    BuyTransactionCommand normalized =
                            buyPlanner.normalize(
                                    buyCommand(
                                            row,
                                            document.batchId(),
                                            source));
                    buyPlan = buyPlanner.planNormalized(normalized);
                    validateNoOpenSell(
                            user.id(),
                            account.existing(),
                            buyPlan.fund().id(),
                            batchOpenSells,
                            row,
                            index,
                            rowIssues);
                    validateSnapshotBoundary(
                            user.id(),
                            account.existing(),
                            buyPlan.effectiveDate(),
                            row,
                            index,
                            rowIssues);
                } catch (BusinessException exception) {
                    rowIssues.add(ImportIssue.error(
                            row,
                            fieldFor(exception.errorCode(), index),
                            issueCode(exception.errorCode()),
                            exception.getMessage()));
                }
            }
            if ("SELL".equals(source.type())) {
                try {
                    SellTransactionCommand normalized =
                            sellPlanner.normalize(
                                    sellCommand(
                                            row,
                                            document.batchId(),
                                            source));
                    var fund = fundCatalogService.getSupportedFund(
                            normalized.fundCode());
                    validateNoOpenSell(
                            user.id(),
                            account.existing(),
                            fund.id(),
                            batchOpenSells,
                            row,
                            index,
                            rowIssues);
                    FundPosition position =
                            projectedPositions.get(fund.id());
                    if (position == null) {
                        throw new BusinessException(
                                ErrorCode.POSITION_NOT_FOUND,
                                "当前账户不存在该基金持仓");
                    }
                    sellPlan = sellPlanner.planNormalized(
                            normalized,
                            fund,
                            position);
                    validateSnapshotBoundary(
                            user.id(),
                            account.existing(),
                            sellPlan.effectiveDate(),
                            row,
                            index,
                            rowIssues);
                } catch (BusinessException exception) {
                    rowIssues.add(ImportIssue.error(
                            row,
                            fieldFor(exception.errorCode(), index),
                            issueCode(exception.errorCode()),
                            exception.getMessage()));
                }
            }

            boolean rejected = rowIssues.stream().anyMatch(
                    issue -> issue.severity()
                            == ImportIssueSeverity.ERROR);
            previews.add(preview(
                    row,
                    source,
                    buyPlan,
                    sellPlan,
                    rejected,
                    rowIssues));
            issues.addAll(rowIssues);
            if (!rejected
                    && (buyPlan != null || sellPlan != null)) {
                long fundId = buyPlan != null
                        ? buyPlan.fund().id()
                        : sellPlan.fund().id();
                plans.add(new TransactionPlan(
                        row,
                        source,
                        buyPlan,
                        sellPlan,
                        positionFingerprint(
                                projectedPositions.get(fundId))));
                applyProjected(
                        user.id(),
                        account.existing(),
                        projectedPositions,
                        batchOpenSells,
                        buyPlan,
                        sellPlan);
            }
        }

        int errorCount = count(
                issues,
                ImportIssueSeverity.ERROR);
        int warningCount = count(
                issues,
                ImportIssueSeverity.WARNING);
        ImportBatchStatus status = errorCount == 0
                ? ImportBatchStatus.READY_TO_COMMIT
                : ImportBatchStatus.PREFLIGHT_FAILED;
        TransactionBatchPreflightResult result =
                new TransactionBatchPreflightResult(
                        document.batchId(),
                        status,
                        document.schemaVersion(),
                        document.importType(),
                        account.preview(),
                        document.transactions().size(),
                        plans.size(),
                        warningCount,
                        errorCount,
                        List.copyOf(previews),
                        List.copyOf(issues));
        return new Analysis(
                account,
                List.copyOf(plans),
                result,
                planHash(account, plans));
    }

    private TransactionBatchRowPreview preview(
            int row,
            TransactionRowDocument source,
            BuyTransactionPlan buyPlan,
            SellTransactionPlan sellPlan,
            boolean rejected,
            List<ImportIssue> issues) {
        var fund = buyPlan != null
                ? buyPlan.fund()
                : sellPlan == null ? null : sellPlan.fund();
        var status = buyPlan != null
                ? buyPlan.status()
                : sellPlan == null ? null : sellPlan.status();
        return new TransactionBatchRowPreview(
                row,
                source.rowId(),
                source.fundCode(),
                fund == null ? null : fund.name(),
                source.type(),
                sellPlan == null
                        ? null
                        : sellPlan.command().sellMode(),
                rejected
                        ? TransactionImportAction.REJECT
                        : TransactionImportAction.IMPORT,
                status,
                source.amount(),
                buyPlan == null ? null : buyPlan.feeAmount(),
                buyPlan == null ? null : buyPlan.netAmount(),
                sellPlan == null
                        ? null
                        : sellPlan.command().expectedAmount(),
                sellPlan == null
                        ? null
                        : sellPlan.command()
                                .actualReceivedAmount(),
                sellPlan == null || sellPlan.impact() == null
                        ? null
                        : sellPlan.impact().removedCost(),
                sellPlan == null || sellPlan.impact() == null
                        ? null
                        : sellPlan.impact().realizedProfit(),
                buyPlan != null
                        ? buyPlan.shares()
                        : sellPlan == null
                                ? null
                                : sellPlan.soldShares(),
                buyPlan != null
                        ? buyPlan.effectiveDate()
                        : sellPlan == null
                                ? null
                                : sellPlan.effectiveDate(),
                buyPlan == null
                        ? null
                        : buyPlan.holdingStartDate(),
                buyPlan != null
                        ? buyPlan.navDate()
                        : sellPlan == null
                                ? null
                                : sellPlan.navDate(),
                buyPlan != null
                        ? buyPlan.unitNav()
                        : sellPlan == null
                                ? null
                                : sellPlan.unitNav(),
                buyPlan != null
                        ? buyPlan.navSource()
                        : sellPlan == null
                                ? null
                                : sellPlan.navSource(),
                buyPlan == null ? null : buyPlan.feeRate(),
                buyPlan == null ? null : buyPlan.feeSource(),
                buyPlan != null
                        ? buyPlan.pendingReason()
                        : sellPlan == null
                                ? null
                                : sellPlan.pendingReason(),
                List.copyOf(issues));
    }

    private void validateSnapshotBoundary(
            long userId,
            FundAccount account,
            LocalDate effectiveDate,
            int row,
            int index,
            List<ImportIssue> issues) {
        if (account == null) {
            return;
        }
        boundaryRepository.findLatestCommittedSnapshotAt(
                        userId,
                        account.id())
                .map(instant -> instant
                        .atZone(clock.getZone())
                        .toLocalDate())
                .filter(snapshotDate ->
                        !effectiveDate.isAfter(snapshotDate))
                .ifPresent(snapshotDate ->
                        issues.add(ImportIssue.error(
                                row,
                                field(index, "submittedDate"),
                                "HISTORY_REBUILD_REQUIRED",
                                "交易不晚于最近持仓快照，不能直接追加，需要先重建历史")));
    }

    private void validateNoOpenSell(
            long userId,
            FundAccount account,
            long fundId,
            Set<Long> batchOpenSells,
            int row,
            int index,
            List<ImportIssue> issues) {
        boolean existingOpen = account != null
                && portfolioRepository.existsOpenSell(
                        userId,
                        account.id(),
                        fundId);
        if (existingOpen || batchOpenSells.contains(fundId)) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "fundCode"),
                    "OPEN_SELL_CONFLICT",
                    "该基金存在待确认或待校准卖出，处理完成前不能继续交易"));
        }
    }

    private void applyProjected(
            long userId,
            FundAccount account,
            Map<Long, FundPosition> positions,
            Set<Long> batchOpenSells,
            BuyTransactionPlan buyPlan,
            SellTransactionPlan sellPlan) {
        Instant now = clock.instant();
        long accountId = account == null ? 0 : account.id();
        if (buyPlan != null
                && (buyPlan.status()
                                == TransactionStatus.CONFIRMED
                        || buyPlan.status()
                                == TransactionStatus.ESTIMATED)) {
            FundPosition current =
                    positions.get(buyPlan.fund().id());
            FundPosition projected = current == null
                    ? FundPosition.fromBuy(
                            userId,
                            accountId,
                            buyPlan.fund().id(),
                            buyPlan.shares(),
                            buyPlan.command().amount(),
                            buyPlan.status(),
                            buyPlan.holdingStartDate(),
                            now)
                    : current.applyBuy(
                            buyPlan.shares(),
                            buyPlan.command().amount(),
                            buyPlan.status(),
                            buyPlan.holdingStartDate(),
                            now);
            positions.put(buyPlan.fund().id(), projected);
            return;
        }
        if (sellPlan == null) {
            return;
        }
        if (sellPlan.appliesToPosition()) {
            FundPosition current =
                    positions.get(sellPlan.fund().id());
            if (sellPlan.impact().clearsPosition()) {
                positions.remove(sellPlan.fund().id());
            } else {
                positions.put(
                        sellPlan.fund().id(),
                        current.applySell(
                                sellPlan.impact(),
                                sellPlan.status(),
                                now));
            }
        }
        if (sellPlan.status()
                        == TransactionStatus.PENDING
                || sellPlan.status()
                        == TransactionStatus.ESTIMATED) {
            batchOpenSells.add(sellPlan.fund().id());
        }
    }

    private AccountMatch matchAccount(
            long userId,
            TransactionBatchDocument document,
            List<ImportIssue> issues) {
        var normalized = accountNameNormalizer.normalize(
                document.account().name());
        AccountPlatform platform =
                parsePlatform(document.account().platform());
        FundAccount sameName = accountRepository
                .findAllByUserId(userId, false)
                .stream()
                .filter(account -> account.normalizedName()
                        .equals(normalized.normalizedName()))
                .findFirst()
                .orElse(null);
        if (sameName != null && sameName.platform() != platform) {
            issues.add(ImportIssue.error(
                    null,
                    "account.platform",
                    "ACCOUNT_PLATFORM_MISMATCH",
                    "已存在同名账户，但平台类型不一致"));
        }
        FundAccount matched = sameName != null
                && sameName.platform() == platform
                        ? sameName
                        : null;
        return new AccountMatch(
                matched,
                normalized.displayName(),
                normalized.normalizedName(),
                platform,
                new SnapshotAccountPreview(
                        matched == null
                                ? null
                                : matched.publicId(),
                        matched == null
                                ? normalized.displayName()
                                : matched.name(),
                        platform,
                        matched == null));
    }

    private List<ImportIssue> validateSchema(
            TransactionBatchDocument document) {
        List<ImportIssue> issues = new ArrayList<>();
        if (!SCHEMA_VERSION.equals(document.schemaVersion())) {
            issues.add(ImportIssue.error(
                    null,
                    "schemaVersion",
                    "UNSUPPORTED_SCHEMA_VERSION",
                    "schemaVersion 必须为 1.0"));
        }
        if (!IMPORT_TYPE.equals(document.importType())) {
            issues.add(ImportIssue.error(
                    null,
                    "importType",
                    "INVALID_IMPORT_TYPE",
                    "importType 必须为 TRANSACTION_BATCH"));
        }
        if (!validBatchId(document.batchId())) {
            issues.add(ImportIssue.error(
                    null,
                    "batchId",
                    "INVALID_BATCH_ID",
                    "batchId 长度必须为 1～64 个字符"));
        }
        validateAccount(document, issues);
        if (document.transactions() == null) {
            issues.add(ImportIssue.error(
                    null,
                    "transactions",
                    "TRANSACTIONS_REQUIRED",
                    "transactions 不能为空"));
            return issues;
        }
        if (document.transactions().isEmpty()) {
            issues.add(ImportIssue.error(
                    null,
                    "transactions",
                    "TRANSACTIONS_EMPTY",
                    "至少需要一条交易"));
        }
        if (document.transactions().size() > MAX_TRANSACTIONS) {
            issues.add(ImportIssue.error(
                    null,
                    "transactions",
                    "TOO_MANY_TRANSACTIONS",
                    "单批最多允许 500 条交易"));
        }
        for (int index = 0;
                index < document.transactions().size();
                index++) {
            validateRow(
                    document.transactions().get(index),
                    index,
                    issues);
        }
        return issues;
    }

    private void validateAccount(
            TransactionBatchDocument document,
            List<ImportIssue> issues) {
        if (document.account() == null) {
            issues.add(ImportIssue.error(
                    null,
                    "account",
                    "ACCOUNT_REQUIRED",
                    "目标账户不能为空"));
            return;
        }
        if (document.account().name() == null
                || document.account().name().isBlank()
                || document.account().name().strip().length() > 100) {
            issues.add(ImportIssue.error(
                    null,
                    "account.name",
                    "INVALID_ACCOUNT_NAME",
                    "账户名称长度必须为 1～100 个字符"));
        }
        if (parsePlatform(document.account().platform()) == null) {
            issues.add(ImportIssue.error(
                    null,
                    "account.platform",
                    "INVALID_ACCOUNT_PLATFORM",
                    "账户平台类型不受支持"));
        }
    }

    private void validateRow(
            TransactionRowDocument value,
            int index,
            List<ImportIssue> issues) {
        int row = index + 1;
        if (value == null) {
            issues.add(ImportIssue.error(
                    row,
                    "transactions[" + index + "]",
                    "TRANSACTION_REQUIRED",
                    "交易行不能为空"));
            return;
        }
        if (value.rowId() == null
                || value.rowId().isBlank()
                || value.rowId().length() > 64
                || !value.rowId().matches("[A-Za-z0-9._:-]+")) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "rowId"),
                    "INVALID_ROW_ID",
                    "rowId 必须为 1～64 位字母、数字或 ._:-"));
        }
        if (value.fundCode() == null
                || !value.fundCode().matches("\\d{6}")) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "fundCode"),
                    "INVALID_FUND_CODE",
                    "基金代码必须为 6 位数字"));
        }
        if (!"BUY".equals(value.type())
                && !"SELL".equals(value.type())) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "type"),
                    "INVALID_TRANSACTION_TYPE",
                    "交易类型必须为 BUY 或 SELL"));
        }
        if ("BUY".equals(value.type())) {
            validatePositive(
                    value.amount(),
                    MONEY_SCALE,
                    row,
                    field(index, "amount"),
                    "INVALID_AMOUNT",
                    "买入金额必须大于 0，且最多 4 位小数",
                    issues);
            if (value.sellMode() != null
                    || value.expectedAmount() != null
                    || value.actualReceivedAmount() != null) {
                issues.add(ImportIssue.error(
                        row,
                        "transactions[" + index + "]",
                        "SELL_FIELD_NOT_ALLOWED",
                        "买入交易不能填写卖出字段"));
            }
        }
        if ("SELL".equals(value.type())) {
            if (value.amount() != null) {
                issues.add(ImportIssue.error(
                        row,
                        field(index, "amount"),
                        "BUY_FIELD_NOT_ALLOWED",
                        "卖出交易不能填写买入金额 amount"));
            }
            SellMode sellMode = parseSellMode(value.sellMode());
            if (sellMode == null) {
                issues.add(ImportIssue.error(
                        row,
                        field(index, "sellMode"),
                        "INVALID_SELL_MODE",
                        "sellMode 必须为 PARTIAL 或 FULL"));
            }
            if (sellMode == SellMode.PARTIAL) {
                validatePositive(
                        value.expectedAmount(),
                        MONEY_SCALE,
                        row,
                        field(index, "expectedAmount"),
                        "INVALID_EXPECTED_AMOUNT",
                        "部分卖出预计到账金额必须大于 0，且最多 4 位小数",
                        issues);
            } else if (value.expectedAmount() != null) {
                validatePositive(
                        value.expectedAmount(),
                        MONEY_SCALE,
                        row,
                        field(index, "expectedAmount"),
                        "INVALID_EXPECTED_AMOUNT",
                        "预计到账金额必须大于 0，且最多 4 位小数",
                        issues);
            }
            if (value.actualReceivedAmount() != null) {
                validatePositive(
                        value.actualReceivedAmount(),
                        MONEY_SCALE,
                        row,
                        field(index, "actualReceivedAmount"),
                        "INVALID_ACTUAL_RECEIVED_AMOUNT",
                        "实际到账金额必须大于 0，且最多 4 位小数",
                        issues);
            }
        }
        if (value.submittedDate() == null) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "submittedDate"),
                    "SUBMITTED_DATE_REQUIRED",
                    "提交日期不能为空"));
        }
        if (parseSubmittedPeriod(value.submittedPeriod()) == null) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "submittedPeriod"),
                    "INVALID_SUBMITTED_PERIOD",
                    "submittedPeriod 必须为 BEFORE_15 或 AFTER_15"));
        }
        if (value.confirmedShares() != null) {
            validatePositive(
                    value.confirmedShares(),
                    SHARE_SCALE,
                    row,
                    field(index, "confirmedShares"),
                    "INVALID_CONFIRMED_SHARES",
                    "平台确认份额必须大于 0，且最多 8 位小数",
                    issues);
        }
        if (value.confirmedDate() != null
                && "BUY".equals(value.type())
                && value.confirmedShares() == null) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "confirmedDate"),
                    "CONFIRMED_SHARES_REQUIRED",
                    "填写确认日期时必须同时填写平台确认份额"));
        }
        if (value.confirmedDate() != null
                && "SELL".equals(value.type())
                && value.confirmedShares() == null
                && value.actualReceivedAmount() == null) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "confirmedDate"),
                    "SELL_CONFIRMATION_REQUIRED",
                    "填写确认日期时必须同时填写确认份额或实际到账金额"));
        }
        if (value.note() != null
                && value.note().length() > 500) {
            issues.add(ImportIssue.error(
                    row,
                    field(index, "note"),
                    "NOTE_TOO_LONG",
                    "备注最多 500 个字符"));
        }
    }

    private BuyTransactionCommand buyCommand(
            int row,
            String batchId,
            TransactionRowDocument value) {
        String note = value.note() == null
                || value.note().isBlank()
                        ? "JSON 批量交易导入：" + batchId
                        : value.note();
        return new BuyTransactionCommand(
                "preflight:" + row,
                "preflight-account",
                value.fundCode(),
                value.amount(),
                value.submittedDate(),
                parseSubmittedPeriod(value.submittedPeriod()),
                value.confirmedShares(),
                value.confirmedDate(),
                note);
    }

    private SellTransactionCommand sellCommand(
            int row,
            String batchId,
            TransactionRowDocument value) {
        String note = value.note() == null
                || value.note().isBlank()
                        ? "JSON 批量交易导入：" + batchId
                        : value.note();
        return new SellTransactionCommand(
                "preflight:" + row,
                "preflight-account",
                value.fundCode(),
                parseSellMode(value.sellMode()),
                value.expectedAmount(),
                value.actualReceivedAmount(),
                value.submittedDate(),
                parseSubmittedPeriod(value.submittedPeriod()),
                value.confirmedShares(),
                value.confirmedDate(),
                note);
    }

    private ParsedDocument parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ParsedDocument(
                    null,
                    null,
                    rawJson == null ? "" : rawJson,
                    fingerprint.create(
                            rawJson == null ? "" : rawJson),
                    List.of(ImportIssue.error(
                            null,
                            "$",
                            "JSON_EMPTY",
                            "JSON 内容不能为空")));
        }
        JsonNode tree;
        try {
            tree = objectMapper.readTree(rawJson);
        } catch (JacksonException exception) {
            return new ParsedDocument(
                    null,
                    null,
                    rawJson,
                    fingerprint.create(rawJson),
                    List.of(ImportIssue.error(
                            null,
                            "$",
                            "JSON_SYNTAX_ERROR",
                            "JSON 语法不正确")));
        }
        String batchId = tree.isObject()
                && tree.path("batchId").isTextual()
                        ? tree.path("batchId").asText()
                        : null;
        try {
            TransactionBatchDocument document = objectMapper
                    .readerFor(TransactionBatchDocument.class)
                    .with(DeserializationFeature
                            .FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(tree);
            return new ParsedDocument(
                    document,
                    batchId,
                    rawJson,
                    null,
                    List.of());
        } catch (UnrecognizedPropertyException exception) {
            return new ParsedDocument(
                    null,
                    batchId,
                    rawJson,
                    fingerprint.create(rawJson),
                    List.of(ImportIssue.error(
                            rowFromPath(exception),
                            path(exception),
                            "UNKNOWN_FIELD",
                            "存在协议未定义字段："
                                    + exception.getPropertyName())));
        } catch (JacksonException | IllegalArgumentException exception) {
            return new ParsedDocument(
                    null,
                    batchId,
                    rawJson,
                    fingerprint.create(rawJson),
                    List.of(ImportIssue.error(
                            null,
                            "$",
                            "SCHEMA_TYPE_ERROR",
                            "字段类型或日期格式不正确")));
        }
    }

    private void savePreflight(
            User user,
            ParsedDocument parsed,
            ImportBatch existing,
            AccountMatch account,
            TransactionBatchPreflightResult result,
            String planHash) {
        Instant now = clock.instant();
        ImportBatch batch = new ImportBatch(
                existing == null ? null : existing.id(),
                existing == null
                        ? UUID.randomUUID().toString()
                        : existing.publicId(),
                user.id(),
                account == null || account.existing() == null
                        ? null
                        : account.existing().id(),
                result.batchId(),
                emptyIfNull(result.schemaVersion()),
                emptyIfNull(result.importType()),
                null,
                null,
                parsed.contentHash() == null
                        ? fingerprint.create(parsed.rawJson())
                        : parsed.contentHash(),
                planHash,
                parsed.rawJson(),
                result.status(),
                result.totalCount(),
                result.importableCount(),
                result.warningCount(),
                result.errorCount(),
                write(result),
                null,
                null,
                existing == null ? now : existing.createdAt(),
                now);
        batchRepository.savePreflight(batch);
    }

    private TransactionBatchPreflightResult schemaFailure(
            ParsedDocument parsed) {
        return new TransactionBatchPreflightResult(
                parsed.batchId(),
                ImportBatchStatus.PREFLIGHT_FAILED,
                null,
                null,
                null,
                0,
                0,
                0,
                parsed.issues().size(),
                List.of(),
                parsed.issues());
    }

    private TransactionBatchPreflightResult schemaFailure(
            TransactionBatchDocument document,
            List<ImportIssue> issues) {
        List<TransactionBatchRowPreview> rows =
                rejectedRows(document, issues);
        return new TransactionBatchPreflightResult(
                document.batchId(),
                ImportBatchStatus.PREFLIGHT_FAILED,
                document.schemaVersion(),
                document.importType(),
                accountPreview(document),
                document.transactions() == null
                        ? 0
                        : document.transactions().size(),
                0,
                count(issues, ImportIssueSeverity.WARNING),
                count(issues, ImportIssueSeverity.ERROR),
                rows,
                List.copyOf(issues));
    }

    private TransactionBatchPreflightResult batchConflict(
            ParsedDocument parsed) {
        ImportIssue issue = ImportIssue.error(
                null,
                "batchId",
                "BATCH_ID_CONFLICT",
                "batchId 已用于另一份导入内容");
        TransactionBatchDocument document = parsed.document();
        return new TransactionBatchPreflightResult(
                parsed.batchId(),
                ImportBatchStatus.PREFLIGHT_FAILED,
                document == null
                        ? null
                        : document.schemaVersion(),
                document == null
                        ? null
                        : document.importType(),
                document == null
                        ? null
                        : accountPreview(document),
                document == null
                                || document.transactions() == null
                        ? 0
                        : document.transactions().size(),
                0,
                0,
                1,
                List.of(),
                List.of(issue));
    }

    private List<TransactionBatchRowPreview> rejectedRows(
            TransactionBatchDocument document,
            List<ImportIssue> issues) {
        if (document.transactions() == null) {
            return List.of();
        }
        List<TransactionBatchRowPreview> rows =
                new ArrayList<>();
        for (int index = 0;
                index < document.transactions().size();
                index++) {
            TransactionRowDocument value =
                    document.transactions().get(index);
            if (value == null) {
                continue;
            }
            int row = index + 1;
            List<ImportIssue> rowIssues = issues.stream()
                    .filter(issue -> Objects.equals(
                            issue.row(),
                            row))
                    .toList();
            rows.add(preview(
                    row,
                    value,
                    null,
                    null,
                    true,
                    rowIssues));
        }
        return List.copyOf(rows);
    }

    private SnapshotAccountPreview accountPreview(
            TransactionBatchDocument document) {
        if (document.account() == null
                || document.account().name() == null) {
            return null;
        }
        return new SnapshotAccountPreview(
                null,
                document.account().name(),
                parsePlatform(document.account().platform()),
                true);
    }

    private FundAccount createAccount(
            long userId,
            AccountMatch account) {
        return accountRepository.save(FundAccount.create(
                userId,
                account.displayName(),
                account.normalizedName(),
                account.platform(),
                clock.instant()));
    }

    private String planHash(
            AccountMatch account,
            List<TransactionPlan> plans) {
        StringBuilder material = new StringBuilder();
        material.append(account.existing() == null
                ? "new:" + account.normalizedName()
                : "existing:"
                        + account.existing().id()
                        + ":"
                        + account.existing().updatedAt());
        for (TransactionPlan value : plans) {
            material.append('|')
                    .append(value.row())
                    .append(':')
                    .append(value.source().rowId())
                    .append(':')
                    .append(value.source().type())
                    .append(':')
                    .append(value.positionFingerprintBefore());
            if (value.buyPlan() != null) {
                BuyTransactionPlan plan = value.buyPlan();
                material.append(':')
                        .append(plan.fund().id())
                        .append(':')
                        .append(plan.status())
                        .append(':')
                        .append(plan.command().amount())
                        .append(':')
                        .append(plan.effectiveDate())
                        .append(':')
                        .append(plan.feeAmount())
                        .append(':')
                        .append(plan.netAmount())
                        .append(':')
                        .append(plan.shares())
                        .append(':')
                        .append(plan.navDate())
                        .append(':')
                        .append(plan.unitNav())
                        .append(':')
                        .append(plan.feeRate())
                        .append(':')
                        .append(plan.holdingStartDate());
            } else {
                SellTransactionPlan plan = value.sellPlan();
                material.append(':')
                        .append(plan.fund().id())
                        .append(':')
                        .append(plan.status())
                        .append(':')
                        .append(plan.command().sellMode())
                        .append(':')
                        .append(plan.amount())
                        .append(':')
                        .append(plan.soldShares())
                        .append(':')
                        .append(plan.effectiveDate())
                        .append(':')
                        .append(plan.navDate())
                        .append(':')
                        .append(plan.unitNav())
                        .append(':')
                        .append(plan.impact());
            }
        }
        return fingerprint.create(material.toString());
    }

    private String positionFingerprint(
            FundPosition position) {
        if (position == null) {
            return "none";
        }
        return position.shares()
                + ":"
                + position.remainingCost()
                + ":"
                + position.status()
                + ":"
                + position.holdingStartDate();
    }

    private String contentHash(
            TransactionBatchDocument document) {
        return fingerprint.create(write(document));
    }

    private TransactionBatchDocument readDocument(String value) {
        try {
            return objectMapper
                    .readerFor(TransactionBatchDocument.class)
                    .with(DeserializationFeature
                            .FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored import request cannot be parsed",
                    exception);
        }
    }

    private TransactionBatchPreflightResult readPreflight(
            String value) {
        try {
            return objectMapper.readValue(
                    value,
                    TransactionBatchPreflightResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored preflight result cannot be parsed",
                    exception);
        }
    }

    private TransactionBatchCommitResult readCommit(
            String value) {
        if (value == null) {
            throw new IllegalStateException(
                    "Committed import has no result");
        }
        try {
            return objectMapper.readValue(
                    value,
                    TransactionBatchCommitResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored commit result cannot be parsed",
                    exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Import data cannot be serialized",
                    exception);
        }
    }

    private User activeUserForUpdate(String publicId) {
        return userRepository.findByPublicIdForUpdate(publicId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        "登录状态已失效"));
    }

    private void validatePositive(
            BigDecimal value,
            int scale,
            int row,
            String field,
            String code,
            String message,
            List<ImportIssue> issues) {
        if (value == null
                || value.signum() <= 0
                || value.scale() > scale) {
            issues.add(ImportIssue.error(
                    row,
                    field,
                    code,
                    message));
        }
    }

    private boolean validBatchId(String batchId) {
        return batchId != null
                && !batchId.isBlank()
                && batchId.length() <= 64
                && batchId.matches("[A-Za-z0-9._:-]+");
    }

    private AccountPlatform parsePlatform(String value) {
        try {
            return value == null
                    ? null
                    : AccountPlatform.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SubmittedPeriod parseSubmittedPeriod(String value) {
        try {
            return value == null
                    ? null
                    : SubmittedPeriod.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SellMode parseSellMode(String value) {
        try {
            return value == null
                    ? null
                    : SellMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String issueCode(ErrorCode errorCode) {
        if (errorCode == ErrorCode.TRANSACTION_BEFORE_SNAPSHOT) {
            return "HISTORY_REBUILD_REQUIRED";
        }
        return errorCode.name();
    }

    private String fieldFor(
            ErrorCode errorCode,
            int index) {
        return switch (errorCode) {
            case FUND_NOT_FOUND, FUND_NOT_SUPPORTED ->
                    field(index, "fundCode");
            case INVALID_TRANSACTION_DATE ->
                    field(index, "submittedDate");
            case TRANSACTION_BEFORE_SNAPSHOT ->
                    field(index, "submittedDate");
            default -> "transactions[" + index + "]";
        };
    }

    private int count(
            List<ImportIssue> issues,
            ImportIssueSeverity severity) {
        return (int) issues.stream()
                .filter(issue -> issue.severity() == severity)
                .count();
    }

    private int rowFromPath(
            UnrecognizedPropertyException exception) {
        return exception.getPath().stream()
                .filter(reference -> reference.getIndex() >= 0)
                .mapToInt(reference ->
                        reference.getIndex() + 1)
                .findFirst()
                .orElse(0);
    }

    private String path(
            UnrecognizedPropertyException exception) {
        StringBuilder path = new StringBuilder();
        for (var reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[')
                        .append(reference.getIndex())
                        .append(']');
            }
        }
        return path.isEmpty() ? "$" : path.toString();
    }

    private String field(int index, String name) {
        return "transactions["
                + index
                + "]."
                + name;
    }

    private String requestId(
            ImportBatch batch,
            int row) {
        return "import:"
                + batch.publicId()
                + ":"
                + row;
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private record ParsedDocument(
            TransactionBatchDocument document,
            String batchId,
            String rawJson,
            String contentHash,
            List<ImportIssue> issues) {

        private ParsedDocument withContentHash(
                String newContentHash) {
            return new ParsedDocument(
                    document,
                    batchId,
                    rawJson,
                    newContentHash,
                    issues);
        }
    }

    private record AccountMatch(
            FundAccount existing,
            String displayName,
            String normalizedName,
            AccountPlatform platform,
            SnapshotAccountPreview preview) {
    }

    private record TransactionPlan(
            int row,
            TransactionRowDocument source,
            BuyTransactionPlan buyPlan,
            SellTransactionPlan sellPlan,
            String positionFingerprintBefore) {
    }

    private record Analysis(
            AccountMatch account,
            List<TransactionPlan> plans,
            TransactionBatchPreflightResult result,
            String planHash) {
    }
}
