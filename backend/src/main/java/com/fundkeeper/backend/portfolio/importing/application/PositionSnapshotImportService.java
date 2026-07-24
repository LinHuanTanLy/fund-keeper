package com.fundkeeper.backend.portfolio.importing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fundkeeper.backend.account.application.AccountNameNormalizer;
import com.fundkeeper.backend.account.domain.AccountPlatform;
import com.fundkeeper.backend.account.domain.FundAccount;
import com.fundkeeper.backend.account.domain.FundAccountRepository;
import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;
import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.OfficialNav;
import com.fundkeeper.backend.portfolio.domain.FundPosition;
import com.fundkeeper.backend.portfolio.domain.FundTransaction;
import com.fundkeeper.backend.portfolio.domain.PortfolioRepository;
import com.fundkeeper.backend.portfolio.domain.PositionStatus;
import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;
import com.fundkeeper.backend.portfolio.domain.TransactionStatus;
import com.fundkeeper.backend.portfolio.importing.application.PositionSnapshotDocument.PositionSnapshotRowDocument;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatch;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchRepository;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;
import com.fundkeeper.backend.portfolio.importing.domain.ImportIssueSeverity;
import com.fundkeeper.backend.portfolio.importing.domain.SnapshotAction;
import com.fundkeeper.backend.portfolio.importing.domain.SnapshotMode;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class PositionSnapshotImportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String IMPORT_TYPE = "POSITION_SNAPSHOT";
    private static final int MAX_POSITIONS = 500;
    private static final int MONEY_SCALE = 4;
    private static final int SHARE_SCALE = 8;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final FundAccountRepository accountRepository;
    private final AccountNameNormalizer accountNameNormalizer;
    private final FundDataRepository fundDataRepository;
    private final PortfolioRepository portfolioRepository;
    private final SnapshotBoundaryRepository boundaryRepository;
    private final ImportBatchRepository batchRepository;
    private final ImportFingerprint fingerprint;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PositionSnapshotImportService(
            ObjectMapper objectMapper,
            UserRepository userRepository,
            FundAccountRepository accountRepository,
            AccountNameNormalizer accountNameNormalizer,
            FundDataRepository fundDataRepository,
            PortfolioRepository portfolioRepository,
            SnapshotBoundaryRepository boundaryRepository,
            ImportBatchRepository batchRepository,
            ImportFingerprint fingerprint,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountNameNormalizer = accountNameNormalizer;
        this.fundDataRepository = fundDataRepository;
        this.portfolioRepository = portfolioRepository;
        this.boundaryRepository = boundaryRepository;
        this.batchRepository = batchRepository;
        this.fingerprint = fingerprint;
        this.clock = clock;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    @Transactional
    public SnapshotPreflightResult preflight(
            String userPublicId,
            String rawJson) {
        User user = activeUserForUpdate(userPublicId);
        ParsedDocument parsed = parse(rawJson);
        if (parsed.document() == null) {
            ImportBatch existing = validBatchId(parsed.batchId())
                    ? batchRepository.findByUserIdAndBatchId(
                                    user.id(),
                                    parsed.batchId())
                            .orElse(null)
                    : null;
            if (existing != null
                    && existing.status()
                            == ImportBatchStatus.COMMITTED) {
                return batchConflict(parsed.batchId());
            }
            SnapshotPreflightResult failed = schemaFailure(parsed);
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
        ImportBatch existing = validBatchId(parsed.document().batchId())
                ? batchRepository.findByUserIdAndBatchId(
                                user.id(),
                                parsed.document().batchId())
                        .orElse(null)
                : null;
        if (existing != null
                && existing.status() == ImportBatchStatus.COMMITTED) {
            if (existing.contentHash().equals(contentHash)) {
                return readPreflight(existing.preflightJson())
                        .withStatus(ImportBatchStatus.COMMITTED);
            }
            return batchConflict(parsed.document());
        }

        if (!schemaIssues.isEmpty()) {
            SnapshotPreflightResult failed =
                    schemaFailure(parsed.document(), schemaIssues);
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

    public SnapshotCommitOutcome commit(
            String userPublicId,
            String batchId) {
        try {
            SnapshotCommitOutcome outcome = transactionTemplate.execute(
                    status -> commitInTransaction(
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

    private SnapshotCommitOutcome commitInTransaction(
            String userPublicId,
            String batchId) {
        User user = activeUserForUpdate(userPublicId);
        ImportBatch batch = batchRepository
                .findByUserIdAndBatchIdForUpdate(user.id(), batchId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.IMPORT_BATCH_NOT_FOUND,
                        "导入批次不存在"));
        if (batch.status() == ImportBatchStatus.COMMITTED) {
            return new SnapshotCommitOutcome(
                    readCommit(batch.commitResultJson()),
                    true);
        }
        if (batch.status() != ImportBatchStatus.READY_TO_COMMIT) {
            throw new BusinessException(
                    ErrorCode.IMPORT_NOT_READY,
                    "导入批次尚未通过预检");
        }

        PositionSnapshotDocument document =
                readDocument(batch.requestJson());
        Analysis analysis = analyze(user, document);
        if (analysis.result().status()
                        != ImportBatchStatus.READY_TO_COMMIT
                || !Objects.equals(
                        analysis.planHash(),
                        batch.planHash())) {
            throw new BusinessException(
                    ErrorCode.IMPORT_NOT_READY,
                    "账户或持仓已发生变化，请重新预检后再确认");
        }

        boolean accountCreated = analysis.account().existing() == null;
        FundAccount account = accountCreated
                ? createAccount(user.id(), document)
                : analysis.account().existing();
        Instant now = clock.instant();
        List<SnapshotCommitResult.CommittedSnapshotRow> committedRows =
                new ArrayList<>();
        int applied = 0;
        int cleared = 0;
        for (PositionPlan plan : analysis.plans()) {
            if (plan.action() == SnapshotAction.UNCHANGED) {
                committedRows.add(
                        new SnapshotCommitResult.CommittedSnapshotRow(
                                plan.row(),
                                plan.fund().code(),
                                plan.action(),
                                null,
                                plan.existing().publicId()));
                continue;
            }

            FundTransaction transaction = portfolioRepository
                    .saveTransaction(adjustment(
                            user.id(),
                            account.id(),
                            batch,
                            document,
                            plan,
                            now));
            if (plan.action() == SnapshotAction.CLEAR) {
                FundPosition locked = portfolioRepository
                        .findPositionByAccountIdAndFundId(
                                account.id(),
                                plan.fund().id())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.IMPORT_NOT_READY,
                                "待清仓持仓已发生变化，请重新预检"));
                portfolioRepository.deletePosition(locked);
                cleared++;
                committedRows.add(
                        new SnapshotCommitResult.CommittedSnapshotRow(
                                plan.row(),
                                plan.fund().code(),
                                plan.action(),
                                transaction.publicId(),
                                null));
                continue;
            }

            FundPosition current = portfolioRepository
                    .findPositionByAccountIdAndFundId(
                            account.id(),
                            plan.fund().id())
                    .orElse(null);
            FundPosition saved = portfolioRepository.savePosition(
                    current == null
                            ? FundPosition.fromSnapshot(
                                    user.id(),
                                    account.id(),
                                    plan.fund().id(),
                                    plan.shares(),
                                    plan.costAmount(),
                                    plan.status(),
                                    plan.holdingStartDate(),
                                    now)
                            : current.applySnapshot(
                                    plan.shares(),
                                    plan.costAmount(),
                                    plan.status(),
                                    plan.holdingStartDate(),
                                    now));
            applied++;
            committedRows.add(
                    new SnapshotCommitResult.CommittedSnapshotRow(
                            plan.row(),
                            plan.fund().code(),
                            plan.action(),
                            transaction.publicId(),
                            saved.publicId()));
        }

        SnapshotCommitResult result = new SnapshotCommitResult(
                batch.batchId(),
                ImportBatchStatus.COMMITTED,
                account.publicId(),
                accountCreated,
                applied,
                cleared,
                List.copyOf(committedRows),
                now);
        batchRepository.markCommitted(
                batch.id(),
                account.id(),
                write(result),
                now);
        return new SnapshotCommitOutcome(result, false);
    }

    private ParsedDocument parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ParsedDocument(
                    null,
                    null,
                    rawJson == null ? "" : rawJson,
                    fingerprint.create(rawJson == null ? "" : rawJson),
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
            PositionSnapshotDocument document = objectMapper
                    .readerFor(PositionSnapshotDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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

    private List<ImportIssue> validateSchema(
            PositionSnapshotDocument document) {
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
                    "importType 必须为 POSITION_SNAPSHOT"));
        }
        if (!validBatchId(document.batchId())) {
            issues.add(ImportIssue.error(
                    null,
                    "batchId",
                    "INVALID_BATCH_ID",
                    "batchId 长度必须为 1～64 个字符"));
        }
        if (parseSnapshotMode(document.snapshotMode()) == null) {
            issues.add(ImportIssue.error(
                    null,
                    "snapshotMode",
                    "INVALID_SNAPSHOT_MODE",
                    "snapshotMode 必须为 FULL_ACCOUNT 或 PARTIAL"));
        }
        if (document.account() == null) {
            issues.add(ImportIssue.error(
                    null,
                    "account",
                    "ACCOUNT_REQUIRED",
                    "目标账户不能为空"));
        } else {
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
        if (document.snapshotAt() == null) {
            issues.add(ImportIssue.error(
                    null,
                    "snapshotAt",
                    "SNAPSHOT_TIME_REQUIRED",
                    "snapshotAt 不能为空"));
        }
        if (document.positions() == null) {
            issues.add(ImportIssue.error(
                    null,
                    "positions",
                    "POSITIONS_REQUIRED",
                    "positions 不能为空"));
            return issues;
        }
        if (document.positions().size() > MAX_POSITIONS) {
            issues.add(ImportIssue.error(
                    null,
                    "positions",
                    "TOO_MANY_POSITIONS",
                    "单批最多允许 500 条持仓"));
        }
        if (document.positions().isEmpty()
                && parseSnapshotMode(document.snapshotMode())
                        == SnapshotMode.PARTIAL) {
            issues.add(ImportIssue.error(
                    null,
                    "positions",
                    "EMPTY_PARTIAL_SNAPSHOT",
                    "PARTIAL 快照至少需要一条持仓"));
        }
        for (int index = 0;
                index < document.positions().size();
                index++) {
            int row = index + 1;
            PositionSnapshotRowDocument value =
                    document.positions().get(index);
            if (value == null) {
                issues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "]",
                        "POSITION_REQUIRED",
                        "持仓行不能为空"));
                continue;
            }
            if (value.fundCode() == null
                    || !value.fundCode().matches("\\d{6}")) {
                issues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].fundCode",
                        "INVALID_FUND_CODE",
                        "基金代码必须为 6 位数字"));
            }
            validatePositiveMoney(
                    value.costAmount(),
                    row,
                    "positions[" + index + "].costAmount",
                    "持仓成本必须大于 0",
                    issues);
            validatePositiveMoney(
                    value.currentAmount(),
                    row,
                    "positions[" + index + "].currentAmount",
                    "当前金额必须大于 0",
                    issues);
            if (value.holdingStartDate() == null) {
                issues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].holdingStartDate",
                        "HOLDING_START_DATE_REQUIRED",
                        "连续持有开始日期不能为空"));
            }
            if (value.confirmedShares() != null
                    && (value.confirmedShares().signum() <= 0
                            || value.confirmedShares().scale()
                                    > SHARE_SCALE)) {
                issues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].confirmedShares",
                        "INVALID_CONFIRMED_SHARES",
                        "平台确认份额必须大于 0，且最多 8 位小数"));
            }
        }
        return issues;
    }

    private Analysis analyze(
            User user,
            PositionSnapshotDocument document) {
        List<ImportIssue> issues = new ArrayList<>();
        Instant now = clock.instant();
        if (document.snapshotAt().toInstant().isAfter(now)) {
            issues.add(ImportIssue.error(
                    null,
                    "snapshotAt",
                    "FUTURE_SNAPSHOT",
                    "快照时间不能晚于当前时间"));
        }

        AccountMatch account = matchAccount(user.id(), document, issues);
        LocalDate snapshotDate = document.snapshotAt().toLocalDate();
        if (account.existing() != null) {
            boundaryRepository.findLatestCommittedSnapshotAt(
                            user.id(),
                            account.existing().id())
                    .filter(latest -> !document.snapshotAt()
                            .toInstant()
                            .isAfter(latest))
                    .ifPresent(latest -> issues.add(ImportIssue.error(
                            null,
                            "snapshotAt",
                            "SNAPSHOT_BOUNDARY_CONFLICT",
                            "快照时间必须晚于该账户最近一次生效快照")));
            boundaryRepository.findLatestPortfolioActivityDate(
                            user.id(),
                            account.existing().id())
                    .filter(latest -> snapshotDate.isBefore(latest))
                    .ifPresent(latest -> issues.add(ImportIssue.error(
                            null,
                            "snapshotAt",
                            "HISTORY_REBUILD_REQUIRED",
                            "快照早于现有交易，需要先执行历史重建")));
        }

        Map<Long, FundPosition> existingByFund =
                existingPositions(user.id(), account.existing());
        Set<String> seenCodes = new HashSet<>();
        Set<Long> includedFundIds = new HashSet<>();
        List<PositionPlan> plans = new ArrayList<>();
        List<SnapshotRowPreview> previews = new ArrayList<>();
        for (int index = 0;
                index < document.positions().size();
                index++) {
            int row = index + 1;
            PositionSnapshotRowDocument value =
                    document.positions().get(index);
            List<ImportIssue> rowIssues = new ArrayList<>();
            if (!seenCodes.add(value.fundCode())) {
                rowIssues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].fundCode",
                        "DUPLICATE_FUND_CODE",
                        "同一快照中基金代码不能重复"));
            }
            FundDefinition fund = fundDataRepository
                    .findFundByCode(value.fundCode())
                    .orElse(null);
            if (fund == null) {
                rowIssues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].fundCode",
                        "FUND_NOT_FOUND",
                        "基金代码不存在"));
            } else if (!fund.supported()) {
                rowIssues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].fundCode",
                        "FUND_NOT_SUPPORTED",
                        "该基金暂不在 V1 支持范围"));
            }
            if (value.holdingStartDate().isAfter(snapshotDate)) {
                rowIssues.add(ImportIssue.error(
                        row,
                        "positions[" + index + "].holdingStartDate",
                        "INVALID_HOLDING_START_DATE",
                        "连续持有开始日期不能晚于快照日期"));
            }

            BigDecimal shares = null;
            OfficialNav nav = null;
            PositionStatus status = null;
            if (fund != null && fund.supported()) {
                includedFundIds.add(fund.id());
                if (value.confirmedShares() != null) {
                    shares = value.confirmedShares().setScale(
                            SHARE_SCALE,
                            RoundingMode.HALF_UP);
                    status = PositionStatus.CONFIRMED;
                } else {
                    nav = fundDataRepository
                            .findLatestOfficialNavOnOrBefore(
                                    fund.id(),
                                    snapshotDate)
                            .orElse(null);
                    if (nav == null || nav.unitNav().signum() <= 0) {
                        rowIssues.add(ImportIssue.error(
                                row,
                                "positions[" + index + "].currentAmount",
                                "OFFICIAL_NAV_UNAVAILABLE",
                                "快照日期缺少可用正式净值，无法推算份额"));
                    } else {
                        shares = value.currentAmount().divide(
                                nav.unitNav(),
                                SHARE_SCALE,
                                RoundingMode.HALF_UP);
                        status = PositionStatus.ESTIMATED;
                        rowIssues.add(ImportIssue.warning(
                                row,
                                "positions[" + index + "].confirmedShares",
                                "SHARES_ESTIMATED",
                                "未提供平台确认份额，将根据当前金额和正式净值推算"));
                    }
                }
            }

            FundPosition existing = fund == null
                    ? null
                    : existingByFund.get(fund.id());
            boolean rejected = rowIssues.stream().anyMatch(
                    issue -> issue.severity()
                            == ImportIssueSeverity.ERROR);
            SnapshotAction action = rejected
                    ? SnapshotAction.REJECT
                    : action(existing, shares, value, status);
            SnapshotRowPreview preview = new SnapshotRowPreview(
                    row,
                    value.fundCode(),
                    fund == null ? null : fund.name(),
                    action,
                    status,
                    normalizeMoney(value.costAmount()),
                    normalizeMoney(value.currentAmount()),
                    shares,
                    value.holdingStartDate(),
                    nav == null ? null : nav.navDate(),
                    nav == null ? null : nav.unitNav(),
                    nav == null ? null : nav.dataSource(),
                    List.copyOf(rowIssues));
            previews.add(preview);
            issues.addAll(rowIssues);
            if (!rejected) {
                plans.add(new PositionPlan(
                        row,
                        fund,
                        existing,
                        action,
                        status,
                        normalizeMoney(value.costAmount()),
                        normalizeMoney(value.currentAmount()),
                        shares,
                        value.holdingStartDate(),
                        nav));
            }
        }

        if (parseSnapshotMode(document.snapshotMode())
                        == SnapshotMode.FULL_ACCOUNT
                && account.existing() != null) {
            List<FundPosition> clearing = existingByFund.values()
                    .stream()
                    .filter(position ->
                            !includedFundIds.contains(position.fundId()))
                    .sorted(Comparator.comparing(FundPosition::createdAt))
                    .toList();
            for (FundPosition existing : clearing) {
                FundDefinition fund = fundDataRepository
                        .findFundById(existing.fundId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Existing position fund no longer exists"));
                int row = document.positions().size()
                        + plans.stream()
                                .filter(plan -> plan.action()
                                        == SnapshotAction.CLEAR)
                                .mapToInt(value -> 1)
                                .sum()
                        + 1;
                ImportIssue warning = ImportIssue.warning(
                        row,
                        "positions",
                        "POSITION_WILL_BE_CLEARED",
                        "FULL_ACCOUNT 未包含该基金，确认后将清仓");
                issues.add(warning);
                previews.add(new SnapshotRowPreview(
                        row,
                        fund.code(),
                        fund.name(),
                        SnapshotAction.CLEAR,
                        null,
                        BigDecimal.ZERO.setScale(MONEY_SCALE),
                        BigDecimal.ZERO.setScale(MONEY_SCALE),
                        BigDecimal.ZERO.setScale(SHARE_SCALE),
                        null,
                        null,
                        null,
                        null,
                        List.of(warning)));
                plans.add(new PositionPlan(
                        row,
                        fund,
                        existing,
                        SnapshotAction.CLEAR,
                        null,
                        BigDecimal.ZERO.setScale(MONEY_SCALE),
                        BigDecimal.ZERO.setScale(MONEY_SCALE),
                        BigDecimal.ZERO.setScale(SHARE_SCALE),
                        null,
                        null));
            }
        }

        int errorCount = count(
                issues,
                ImportIssueSeverity.ERROR);
        int warningCount = count(
                issues,
                ImportIssueSeverity.WARNING);
        int importableCount = (int) previews.stream()
                .limit(document.positions().size())
                .filter(row -> row.action() != SnapshotAction.REJECT)
                .count();
        ImportBatchStatus status = errorCount == 0
                ? ImportBatchStatus.READY_TO_COMMIT
                : ImportBatchStatus.PREFLIGHT_FAILED;
        SnapshotPreflightResult result = new SnapshotPreflightResult(
                document.batchId(),
                status,
                document.schemaVersion(),
                document.importType(),
                parseSnapshotMode(document.snapshotMode()),
                document.snapshotAt(),
                account.preview(),
                document.positions().size(),
                importableCount,
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

    private AccountMatch matchAccount(
            long userId,
            PositionSnapshotDocument document,
            List<ImportIssue> issues) {
        var normalized = accountNameNormalizer.normalize(
                document.account().name());
        AccountPlatform platform = parsePlatform(
                document.account().platform());
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
            return new AccountMatch(
                    sameName,
                    normalized.displayName(),
                    normalized.normalizedName(),
                    platform,
                    new SnapshotAccountPreview(
                            sameName.publicId(),
                            sameName.name(),
                            sameName.platform(),
                            false));
        }
        if (sameName != null) {
            return new AccountMatch(
                    sameName,
                    normalized.displayName(),
                    normalized.normalizedName(),
                    platform,
                    new SnapshotAccountPreview(
                            sameName.publicId(),
                            sameName.name(),
                            sameName.platform(),
                            false));
        }
        return new AccountMatch(
                null,
                normalized.displayName(),
                normalized.normalizedName(),
                platform,
                new SnapshotAccountPreview(
                        null,
                        normalized.displayName(),
                        platform,
                        true));
    }

    private Map<Long, FundPosition> existingPositions(
            long userId,
            FundAccount account) {
        if (account == null) {
            return Map.of();
        }
        Map<Long, FundPosition> positions = new HashMap<>();
        for (FundPosition position :
                portfolioRepository.findPositionsByUserIdAndAccountId(
                        userId,
                        account.id())) {
            positions.put(position.fundId(), position);
        }
        return positions;
    }

    private SnapshotAction action(
            FundPosition existing,
            BigDecimal shares,
            PositionSnapshotRowDocument value,
            PositionStatus status) {
        if (existing == null) {
            return SnapshotAction.ADD;
        }
        boolean unchanged = existing.shares().compareTo(shares) == 0
                && existing.remainingCost().compareTo(
                        normalizeMoney(value.costAmount())) == 0
                && Objects.equals(
                        existing.holdingStartDate(),
                        value.holdingStartDate())
                && existing.status() == status;
        return unchanged
                ? SnapshotAction.UNCHANGED
                : SnapshotAction.CALIBRATE;
    }

    private FundTransaction adjustment(
            long userId,
            long accountId,
            ImportBatch batch,
            PositionSnapshotDocument document,
            PositionPlan plan,
            Instant now) {
        LocalDate snapshotDate = document.snapshotAt().toLocalDate();
        boolean confirmed = plan.action() == SnapshotAction.CLEAR
                || plan.status() == PositionStatus.CONFIRMED;
        return FundTransaction.createPositionAdjustment(
                userId,
                accountId,
                plan.fund().id(),
                "snapshot:"
                        + batch.publicId()
                        + ":"
                        + plan.row(),
                batch.contentHash(),
                confirmed
                        ? TransactionStatus.CONFIRMED
                        : TransactionStatus.ESTIMATED,
                plan.costAmount(),
                plan.currentAmount(),
                plan.shares(),
                snapshotDate,
                document.snapshotAt().toLocalTime()
                                .isBefore(LocalTime.of(15, 0))
                        ? SubmittedPeriod.BEFORE_15
                        : SubmittedPeriod.AFTER_15,
                confirmed ? snapshotDate : null,
                plan.nav() == null ? null : plan.nav().navDate(),
                plan.nav() == null ? null : plan.nav().unitNav(),
                plan.nav() == null ? null : plan.nav().dataSource(),
                "JSON 持仓快照导入：" + batch.batchId(),
                now);
    }

    private FundAccount createAccount(
            long userId,
            PositionSnapshotDocument document) {
        var normalized = accountNameNormalizer.normalize(
                document.account().name());
        return accountRepository.save(FundAccount.create(
                userId,
                normalized.displayName(),
                normalized.normalizedName(),
                parsePlatform(document.account().platform()),
                clock.instant()));
    }

    private void savePreflight(
            User user,
            ParsedDocument parsed,
            ImportBatch existing,
            AccountMatch account,
            SnapshotPreflightResult result,
            String planHash) {
        Instant now = clock.instant();
        PositionSnapshotDocument document = parsed.document();
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
                result.snapshotMode() == null
                        ? null
                        : result.snapshotMode().name(),
                result.snapshotAt() == null
                        ? null
                        : result.snapshotAt().toInstant(),
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

    private SnapshotPreflightResult schemaFailure(
            ParsedDocument parsed) {
        return new SnapshotPreflightResult(
                parsed.batchId(),
                ImportBatchStatus.PREFLIGHT_FAILED,
                null,
                null,
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

    private SnapshotPreflightResult schemaFailure(
            PositionSnapshotDocument document,
            List<ImportIssue> issues) {
        List<SnapshotRowPreview> rows = rejectedRows(
                document,
                issues);
        return new SnapshotPreflightResult(
                document.batchId(),
                ImportBatchStatus.PREFLIGHT_FAILED,
                document.schemaVersion(),
                document.importType(),
                parseSnapshotMode(document.snapshotMode()),
                document.snapshotAt(),
                accountPreview(document),
                document.positions() == null
                        ? 0
                        : document.positions().size(),
                0,
                count(issues, ImportIssueSeverity.WARNING),
                count(issues, ImportIssueSeverity.ERROR),
                rows,
                List.copyOf(issues));
    }

    private SnapshotPreflightResult batchConflict(
            PositionSnapshotDocument document) {
        return batchConflict(
                document.batchId(),
                document.schemaVersion(),
                document.importType(),
                parseSnapshotMode(document.snapshotMode()),
                document.snapshotAt(),
                accountPreview(document),
                document.positions() == null
                        ? 0
                        : document.positions().size());
    }

    private SnapshotPreflightResult batchConflict(String batchId) {
        return batchConflict(
                batchId,
                null,
                null,
                null,
                null,
                null,
                0);
    }

    private SnapshotPreflightResult batchConflict(
            String batchId,
            String schemaVersion,
            String importType,
            SnapshotMode snapshotMode,
            OffsetDateTime snapshotAt,
            SnapshotAccountPreview account,
            int totalCount) {
        ImportIssue issue = ImportIssue.error(
                null,
                "batchId",
                "BATCH_ID_CONFLICT",
                "batchId 已用于另一份已提交内容");
        return new SnapshotPreflightResult(
                batchId,
                ImportBatchStatus.PREFLIGHT_FAILED,
                schemaVersion,
                importType,
                snapshotMode,
                snapshotAt,
                account,
                totalCount,
                0,
                0,
                1,
                List.of(),
                List.of(issue));
    }

    private List<SnapshotRowPreview> rejectedRows(
            PositionSnapshotDocument document,
            List<ImportIssue> issues) {
        if (document.positions() == null) {
            return List.of();
        }
        List<SnapshotRowPreview> rows = new ArrayList<>();
        for (int index = 0;
                index < document.positions().size();
                index++) {
            PositionSnapshotRowDocument row =
                    document.positions().get(index);
            if (row == null) {
                continue;
            }
            int number = index + 1;
            List<ImportIssue> rowIssues = issues.stream()
                    .filter(issue -> Objects.equals(issue.row(), number))
                    .toList();
            rows.add(new SnapshotRowPreview(
                    number,
                    row.fundCode(),
                    null,
                    SnapshotAction.REJECT,
                    null,
                    row.costAmount(),
                    row.currentAmount(),
                    row.confirmedShares(),
                    row.holdingStartDate(),
                    null,
                    null,
                    null,
                    rowIssues));
        }
        return List.copyOf(rows);
    }

    private SnapshotAccountPreview accountPreview(
            PositionSnapshotDocument document) {
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

    private String planHash(
            AccountMatch account,
            List<PositionPlan> plans) {
        StringBuilder material = new StringBuilder();
        material.append(account.existing() == null
                ? "new:" + account.normalizedName()
                : "existing:" + account.existing().id());
        for (PositionPlan plan : plans) {
            material.append('|')
                    .append(plan.row())
                    .append(':')
                    .append(plan.fund().id())
                    .append(':')
                    .append(plan.action())
                    .append(':')
                    .append(plan.costAmount())
                    .append(':')
                    .append(plan.shares())
                    .append(':')
                    .append(plan.status());
            if (plan.existing() != null) {
                material.append(':')
                        .append(plan.existing().id())
                        .append(':')
                        .append(plan.existing().shares())
                        .append(':')
                        .append(plan.existing().remainingCost())
                        .append(':')
                        .append(plan.existing().status())
                        .append(':')
                        .append(plan.existing().updatedAt());
            }
        }
        return fingerprint.create(material.toString());
    }

    private String contentHash(
            PositionSnapshotDocument document) {
        return fingerprint.create(write(document));
    }

    private PositionSnapshotDocument readDocument(String value) {
        try {
            return objectMapper
                    .readerFor(PositionSnapshotDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored import request cannot be parsed",
                    exception);
        }
    }

    private SnapshotPreflightResult readPreflight(String value) {
        try {
            return objectMapper.readValue(
                    value,
                    SnapshotPreflightResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored preflight result cannot be parsed",
                    exception);
        }
    }

    private SnapshotCommitResult readCommit(String value) {
        if (value == null) {
            throw new IllegalStateException(
                    "Committed import has no result");
        }
        try {
            return objectMapper.readValue(
                    value,
                    SnapshotCommitResult.class);
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

    private void validatePositiveMoney(
            BigDecimal value,
            int row,
            String field,
            String message,
            List<ImportIssue> issues) {
        if (value == null
                || value.signum() <= 0
                || value.scale() > MONEY_SCALE) {
            issues.add(ImportIssue.error(
                    row,
                    field,
                    "INVALID_MONEY",
                    message + "，且最多 4 位小数"));
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean validBatchId(String batchId) {
        return batchId != null
                && !batchId.isBlank()
                && batchId.length() <= 64;
    }

    private SnapshotMode parseSnapshotMode(String value) {
        try {
            return value == null
                    ? null
                    : SnapshotMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
                .mapToInt(reference -> reference.getIndex() + 1)
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

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private record ParsedDocument(
            PositionSnapshotDocument document,
            String batchId,
            String rawJson,
            String contentHash,
            List<ImportIssue> issues) {

        private ParsedDocument withContentHash(String newContentHash) {
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

    private record PositionPlan(
            int row,
            FundDefinition fund,
            FundPosition existing,
            SnapshotAction action,
            PositionStatus status,
            BigDecimal costAmount,
            BigDecimal currentAmount,
            BigDecimal shares,
            LocalDate holdingStartDate,
            OfficialNav nav) {
    }

    private record Analysis(
            AccountMatch account,
            List<PositionPlan> plans,
            SnapshotPreflightResult result,
            String planHash) {
    }
}
