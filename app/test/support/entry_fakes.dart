import 'package:decimal/decimal.dart';
import 'package:fund_keeper/features/entry/data/entry_remote_data_source.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';

class FakeEntryRemoteDataSource implements EntryRemoteDataSource {
  ManualBuyResult manualBuyResult = ManualBuyResult(
    id: 'transaction-1',
    requestId: 'request-placeholder',
    fundCode: '005827',
    fundName: '易方达蓝筹精选混合',
    status: 'ESTIMATED',
    amount: Decimal.parse('1000.50'),
    shares: Decimal.parse('486.12345678'),
    effectiveTradeDate: DateTime(2026, 7, 27),
    pendingReason: null,
  );
  ManualSellResult manualSellResult = ManualSellResult(
    id: 'sell-transaction-1',
    requestId: 'request-placeholder',
    fundCode: '005827',
    fundName: '易方达蓝筹精选混合',
    sellMode: 'PARTIAL',
    status: 'ESTIMATED',
    expectedAmount: Decimal.parse('800.50'),
    shares: Decimal.parse('388.12345678'),
    effectiveTradeDate: DateTime(2026, 7, 27),
    pendingReason: null,
  );
  ImportPreflightResult preflightResult = importPreflightFixture();
  ImportCommitResult commitResult = importCommitFixture();
  Object? manualBuyFailure;
  Object? manualSellFailure;
  Object? preflightFailure;
  Object? commitFailure;
  int manualBuyCalls = 0;
  int manualSellCalls = 0;
  int preflightCalls = 0;
  int commitCalls = 0;
  final manualBuyDrafts = <ManualBuyDraft>[];
  final manualSellDrafts = <ManualSellDraft>[];
  ImportKind? lastPreflightKind;
  String? lastRawJson;
  ImportKind? lastCommitKind;
  String? lastCommitBatchId;

  @override
  Future<ManualBuyResult> createManualBuy(ManualBuyDraft draft) async {
    manualBuyCalls += 1;
    manualBuyDrafts.add(draft);
    final failure = manualBuyFailure;
    manualBuyFailure = null;
    if (failure != null) {
      throw failure;
    }
    return ManualBuyResult(
      id: manualBuyResult.id,
      requestId: draft.requestId,
      fundCode: manualBuyResult.fundCode,
      fundName: manualBuyResult.fundName,
      status: manualBuyResult.status,
      amount: manualBuyResult.amount,
      shares: manualBuyResult.shares,
      effectiveTradeDate: manualBuyResult.effectiveTradeDate,
      pendingReason: manualBuyResult.pendingReason,
    );
  }

  @override
  Future<ManualSellResult> createManualSell(ManualSellDraft draft) async {
    manualSellCalls += 1;
    manualSellDrafts.add(draft);
    final failure = manualSellFailure;
    manualSellFailure = null;
    if (failure != null) {
      throw failure;
    }
    return ManualSellResult(
      id: manualSellResult.id,
      requestId: draft.requestId,
      fundCode: draft.fundCode,
      fundName: manualSellResult.fundName,
      sellMode: draft.sellMode.apiValue,
      status: manualSellResult.status,
      expectedAmount: draft.expectedAmount,
      shares: manualSellResult.shares,
      effectiveTradeDate: manualSellResult.effectiveTradeDate,
      pendingReason: manualSellResult.pendingReason,
    );
  }

  @override
  Future<ImportPreflightResult> preflightImport(
    ImportKind kind,
    String rawJson,
  ) async {
    preflightCalls += 1;
    lastPreflightKind = kind;
    lastRawJson = rawJson;
    final failure = preflightFailure;
    preflightFailure = null;
    if (failure != null) {
      throw failure;
    }
    return preflightResult;
  }

  @override
  Future<ImportCommitResult> commitImport(
    ImportKind kind,
    String batchId,
  ) async {
    commitCalls += 1;
    lastCommitKind = kind;
    lastCommitBatchId = batchId;
    final failure = commitFailure;
    commitFailure = null;
    if (failure != null) {
      throw failure;
    }
    return commitResult;
  }
}

ImportPreflightResult importPreflightFixture({
  String status = 'READY_TO_COMMIT',
  int errorCount = 0,
}) {
  return ImportPreflightResult(
    batchId: 'batch-001',
    status: status,
    importType: 'TRANSACTION_BATCH',
    accountName: '我的支付宝',
    accountWillCreate: false,
    totalCount: 1,
    importableCount: errorCount == 0 ? 1 : 0,
    warningCount: 0,
    errorCount: errorCount,
    calibrationCount: 0,
    rows: [
      ImportRowPreview(
        row: 1,
        rowId: 'row-001',
        fundCode: '005827',
        fundName: '易方达蓝筹精选混合',
        action: errorCount == 0 ? 'IMPORT' : 'REJECT',
        resultStatus: errorCount == 0 ? 'ESTIMATED' : null,
        issues: errorCount == 0
            ? const []
            : const [
                ImportIssuePreview(
                  row: 1,
                  field: 'fundCode',
                  code: 'FUND_NOT_FOUND',
                  message: '基金不存在',
                  severity: 'ERROR',
                ),
              ],
      ),
    ],
    issues: const [],
  );
}

ImportCommitResult importCommitFixture() {
  return ImportCommitResult(
    batchId: 'batch-001',
    status: 'COMMITTED',
    accountId: 'account-1',
    accountCreated: false,
    appliedCount: 1,
    committedAt: DateTime.parse('2026-07-27T08:00:00Z'),
  );
}
