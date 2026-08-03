import 'package:decimal/decimal.dart';
import 'package:fund_keeper/features/transaction/data/transaction_remote_data_source.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';

class FakeTransactionRemoteDataSource implements TransactionRemoteDataSource {
  FakeTransactionRemoteDataSource({
    List<TransactionRecord>? records,
    this.pageSize = 20,
  }) : records = records ?? [];

  List<TransactionRecord> records;
  int pageSize;
  int listCalls = 0;
  int confirmBuyCalls = 0;
  int confirmSellCalls = 0;
  int cancelCalls = 0;
  TransactionFilters? lastFilters;
  final requestedPages = <int>[];
  BuyConfirmationDraft? lastBuyDraft;
  SellConfirmationDraft? lastSellDraft;
  String? lastCancellationReason;
  Object? listFailure;
  Object? actionFailure;

  @override
  Future<TransactionPage> listTransactions({
    required TransactionFilters filters,
    required int page,
    required int size,
  }) async {
    listCalls += 1;
    lastFilters = filters;
    requestedPages.add(page);
    final failure = listFailure;
    listFailure = null;
    if (failure != null) {
      throw failure;
    }
    final filtered = records
        .where((record) {
          return (filters.accountId == null ||
                  record.accountId == filters.accountId) &&
              (filters.fundCode == null ||
                  record.fundCode == filters.fundCode) &&
              (filters.type == null || record.type == filters.type) &&
              (filters.status == null || record.status == filters.status);
        })
        .toList(growable: false);
    final start = page * pageSize;
    final end = start + pageSize > filtered.length
        ? filtered.length
        : start + pageSize;
    final items = start >= filtered.length
        ? const <TransactionRecord>[]
        : filtered.sublist(start, end);
    final totalPages = filtered.isEmpty
        ? 0
        : ((filtered.length + pageSize - 1) ~/ pageSize);
    return TransactionPage(
      items: items,
      page: page,
      size: size,
      totalElements: filtered.length,
      totalPages: totalPages,
    );
  }

  @override
  Future<TransactionRecord> confirmBuy(
    String transactionId,
    BuyConfirmationDraft draft,
  ) async {
    confirmBuyCalls += 1;
    lastBuyDraft = draft;
    _throwActionFailure();
    return _replace(
      transactionId,
      status: 'CONFIRMED',
      shares: draft.confirmedShares,
      confirmedDate: draft.confirmedDate,
    );
  }

  @override
  Future<TransactionRecord> confirmSell(
    String transactionId,
    SellConfirmationDraft draft,
  ) async {
    confirmSellCalls += 1;
    lastSellDraft = draft;
    _throwActionFailure();
    return _replace(
      transactionId,
      status: 'CONFIRMED',
      shares: draft.confirmedShares,
      actualReceivedAmount: draft.actualReceivedAmount,
      confirmedDate: draft.confirmedDate,
    );
  }

  @override
  Future<TransactionRecord> cancel(String transactionId, String? reason) async {
    cancelCalls += 1;
    lastCancellationReason = reason;
    _throwActionFailure();
    return _replace(
      transactionId,
      status: 'CANCELLED',
      cancellationReason: reason,
    );
  }

  void _throwActionFailure() {
    final failure = actionFailure;
    actionFailure = null;
    if (failure != null) {
      throw failure;
    }
  }

  TransactionRecord _replace(
    String id, {
    required String status,
    Decimal? shares,
    Decimal? actualReceivedAmount,
    DateTime? confirmedDate,
    String? cancellationReason,
  }) {
    final index = records.indexWhere((record) => record.id == id);
    final old = records[index];
    final updated = transactionFixture(
      id: old.id,
      type: old.type,
      status: status,
      sellMode: old.sellMode,
      fundCode: old.fundCode,
      fundName: old.fundName,
      amount: old.amount,
      expectedAmount: old.expectedAmount,
      actualReceivedAmount: actualReceivedAmount ?? old.actualReceivedAmount,
      shares: shares ?? old.shares,
      confirmedDate: confirmedDate ?? old.confirmedDate,
      cancellationReason: cancellationReason ?? old.cancellationReason,
    );
    records = [...records]..[index] = updated;
    return updated;
  }
}

TransactionRecord transactionFixture({
  required String id,
  required String type,
  required String status,
  String? sellMode,
  String fundCode = '005827',
  String fundName = '易方达蓝筹精选混合',
  Decimal? amount,
  Decimal? expectedAmount,
  Decimal? actualReceivedAmount,
  Decimal? shares,
  DateTime? confirmedDate,
  String? cancellationReason,
}) {
  return TransactionRecord(
    id: id,
    requestId: 'request-$id',
    accountId: 'account-1',
    accountName: '默认账户',
    fundCode: fundCode,
    fundName: fundName,
    type: type,
    sellMode: sellMode,
    status: status,
    amount: amount ?? (type == 'BUY' ? Decimal.parse('1000') : null),
    expectedAmount:
        expectedAmount ?? (type == 'SELL' ? Decimal.parse('800') : null),
    actualReceivedAmount: actualReceivedAmount,
    removedCost: type == 'SELL' ? Decimal.parse('700') : null,
    realizedProfit: actualReceivedAmount == null
        ? null
        : actualReceivedAmount - Decimal.parse('700'),
    shares: shares ?? Decimal.parse('400'),
    submittedDate: DateTime(2026, 7, 27),
    submittedPeriod: 'BEFORE_15',
    effectiveTradeDate: DateTime(2026, 7, 27),
    confirmedDate: confirmedDate,
    pendingReason: status == 'PENDING'
        ? type == 'SELL'
              ? 'SELL_CONFIRMATION_REQUIRED'
              : 'OFFICIAL_NAV_UNAVAILABLE'
        : null,
    cancellationReason: cancellationReason,
    createdAt: DateTime.parse('2026-07-27T08:00:00Z'),
  );
}
