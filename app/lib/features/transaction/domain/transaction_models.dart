import 'package:decimal/decimal.dart';

class TransactionRecord {
  const TransactionRecord({
    required this.id,
    required this.requestId,
    required this.accountId,
    required this.accountName,
    required this.fundCode,
    required this.fundName,
    required this.type,
    required this.sellMode,
    required this.status,
    required this.amount,
    required this.expectedAmount,
    required this.actualReceivedAmount,
    required this.removedCost,
    required this.realizedProfit,
    required this.shares,
    required this.submittedDate,
    required this.submittedPeriod,
    required this.effectiveTradeDate,
    required this.confirmedDate,
    required this.pendingReason,
    required this.cancellationReason,
    required this.createdAt,
  });

  factory TransactionRecord.fromJson(Map<String, dynamic> json) {
    return TransactionRecord(
      id: _requiredString(json, 'id'),
      requestId: _requiredString(json, 'requestId'),
      accountId: _requiredString(json, 'accountId'),
      accountName: _requiredString(json, 'accountName'),
      fundCode: _requiredString(json, 'fundCode'),
      fundName: _requiredString(json, 'fundName'),
      type: _requiredString(json, 'type'),
      sellMode: json['sellMode'] as String?,
      status: _requiredString(json, 'status'),
      amount: _decimalOrNull(json['amount']),
      expectedAmount: _decimalOrNull(json['expectedAmount']),
      actualReceivedAmount: _decimalOrNull(json['actualReceivedAmount']),
      removedCost: _decimalOrNull(json['removedCost']),
      realizedProfit: _decimalOrNull(json['realizedProfit']),
      shares: _decimalOrNull(json['shares']),
      submittedDate: _dateOrNull(json['submittedDate']),
      submittedPeriod: json['submittedPeriod'] as String?,
      effectiveTradeDate: _dateOrNull(json['effectiveTradeDate']),
      confirmedDate: _dateOrNull(json['confirmedDate']),
      pendingReason: json['pendingReason'] as String?,
      cancellationReason: json['cancellationReason'] as String?,
      createdAt: _dateOrNull(json['createdAt']),
    );
  }

  final String id;
  final String requestId;
  final String accountId;
  final String accountName;
  final String fundCode;
  final String fundName;
  final String type;
  final String? sellMode;
  final String status;
  final Decimal? amount;
  final Decimal? expectedAmount;
  final Decimal? actualReceivedAmount;
  final Decimal? removedCost;
  final Decimal? realizedProfit;
  final Decimal? shares;
  final DateTime? submittedDate;
  final String? submittedPeriod;
  final DateTime? effectiveTradeDate;
  final DateTime? confirmedDate;
  final String? pendingReason;
  final String? cancellationReason;
  final DateTime? createdAt;

  bool get isOpen => status == 'PENDING' || status == 'ESTIMATED';
  bool get isBuy => type == 'BUY';
  bool get isSell => type == 'SELL';
  bool get isPartialSell => isSell && sellMode == 'PARTIAL';
  bool get canConfirm => isOpen && (isBuy || isSell);
  bool get canCancel => isOpen && (isBuy || isSell);
}

class TransactionPage {
  const TransactionPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  factory TransactionPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('Missing transaction items.');
    }
    return TransactionPage(
      items: rawItems
          .whereType<Map<Object?, Object?>>()
          .map(
            (item) =>
                TransactionRecord.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList(growable: false),
      page: (json['page'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      totalElements: (json['totalElements'] as num).toInt(),
      totalPages: (json['totalPages'] as num).toInt(),
    );
  }

  final List<TransactionRecord> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  bool get hasNextPage => page + 1 < totalPages;
}

class TransactionFilters {
  const TransactionFilters({
    this.accountId,
    this.fundCode,
    this.type,
    this.status,
  });

  final String? accountId;
  final String? fundCode;
  final String? type;
  final String? status;

  TransactionFilters copyWith({
    String? accountId,
    bool clearAccount = false,
    String? fundCode,
    bool clearFundCode = false,
    String? type,
    bool clearType = false,
    String? status,
    bool clearStatus = false,
  }) {
    return TransactionFilters(
      accountId: clearAccount ? null : accountId ?? this.accountId,
      fundCode: clearFundCode ? null : fundCode ?? this.fundCode,
      type: clearType ? null : type ?? this.type,
      status: clearStatus ? null : status ?? this.status,
    );
  }
}

class TransactionHistoryState {
  const TransactionHistoryState({
    required this.filters,
    required this.items,
    required this.page,
    required this.totalPages,
    required this.totalElements,
    this.isRefreshing = false,
    this.isLoadingMore = false,
    this.loadMoreError,
  });

  final TransactionFilters filters;
  final List<TransactionRecord> items;
  final int page;
  final int totalPages;
  final int totalElements;
  final bool isRefreshing;
  final bool isLoadingMore;
  final String? loadMoreError;

  bool get hasNextPage => page + 1 < totalPages;

  TransactionHistoryState copyWith({
    TransactionFilters? filters,
    List<TransactionRecord>? items,
    int? page,
    int? totalPages,
    int? totalElements,
    bool? isRefreshing,
    bool? isLoadingMore,
    String? loadMoreError,
    bool clearLoadMoreError = false,
  }) {
    return TransactionHistoryState(
      filters: filters ?? this.filters,
      items: items ?? this.items,
      page: page ?? this.page,
      totalPages: totalPages ?? this.totalPages,
      totalElements: totalElements ?? this.totalElements,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      loadMoreError: clearLoadMoreError
          ? null
          : loadMoreError ?? this.loadMoreError,
    );
  }
}

class BuyConfirmationDraft {
  const BuyConfirmationDraft({
    required this.confirmedShares,
    required this.confirmedDate,
  });

  final Decimal confirmedShares;
  final DateTime? confirmedDate;

  Map<String, Object?> toJson() => {
    'confirmedShares': num.parse(confirmedShares.toString()),
    'confirmedDate': _dateString(confirmedDate),
  };
}

class SellConfirmationDraft {
  const SellConfirmationDraft({
    required this.actualReceivedAmount,
    required this.confirmedShares,
    required this.confirmedDate,
  });

  final Decimal actualReceivedAmount;
  final Decimal? confirmedShares;
  final DateTime? confirmedDate;

  Map<String, Object?> toJson() => {
    'actualReceivedAmount': num.parse(actualReceivedAmount.toString()),
    'confirmedShares': confirmedShares == null
        ? null
        : num.parse(confirmedShares.toString()),
    'confirmedDate': _dateString(confirmedDate),
  };
}

Decimal? _decimalOrNull(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is num || value is String) {
    return Decimal.parse(value.toString());
  }
  throw const FormatException('Expected a decimal value.');
}

DateTime? _dateOrNull(Object? value) {
  return value is String ? DateTime.tryParse(value) : null;
}

String? _dateString(DateTime? value) {
  if (value == null) {
    return null;
  }
  String two(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${two(value.month)}-${two(value.day)}';
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.isNotEmpty) {
    return value;
  }
  throw FormatException('Missing required string: $key');
}
