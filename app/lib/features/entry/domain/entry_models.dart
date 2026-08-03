import 'package:decimal/decimal.dart';

enum SubmittedPeriod {
  before15('BEFORE_15'),
  after15('AFTER_15');

  const SubmittedPeriod(this.apiValue);

  final String apiValue;
}

enum SellMode {
  partial('PARTIAL'),
  full('FULL');

  const SellMode(this.apiValue);

  final String apiValue;
}

enum ImportKind {
  positionSnapshot(
    apiValue: 'POSITION_SNAPSHOT',
    pathSegment: 'position-snapshots',
  ),
  transactionBatch(
    apiValue: 'TRANSACTION_BATCH',
    pathSegment: 'transaction-batches',
  );

  const ImportKind({required this.apiValue, required this.pathSegment});

  final String apiValue;
  final String pathSegment;
}

class ManualBuyDraft {
  const ManualBuyDraft({
    required this.requestId,
    required this.accountId,
    required this.fundCode,
    required this.amount,
    required this.submittedDate,
    required this.submittedPeriod,
  });

  final String requestId;
  final String accountId;
  final String fundCode;
  final Decimal amount;
  final DateTime submittedDate;
  final SubmittedPeriod submittedPeriod;

  Map<String, Object?> toJson() {
    return {
      'requestId': requestId,
      'accountId': accountId,
      'fundCode': fundCode,
      'amount': num.parse(amount.toString()),
      'submittedDate': _date(submittedDate),
      'submittedPeriod': submittedPeriod.apiValue,
      'confirmedShares': null,
      'confirmedDate': null,
      'note': 'Flutter 手动录入',
    };
  }
}

class ManualBuyResult {
  const ManualBuyResult({
    required this.id,
    required this.requestId,
    required this.fundCode,
    required this.fundName,
    required this.status,
    required this.amount,
    required this.shares,
    required this.effectiveTradeDate,
    required this.pendingReason,
  });

  factory ManualBuyResult.fromJson(Map<String, dynamic> json) {
    return ManualBuyResult(
      id: _requiredString(json, 'id'),
      requestId: _requiredString(json, 'requestId'),
      fundCode: _requiredString(json, 'fundCode'),
      fundName: _requiredString(json, 'fundName'),
      status: _requiredString(json, 'status'),
      amount: _decimal(json['amount']),
      shares: _decimalOrNull(json['shares']),
      effectiveTradeDate: _dateOrNull(json['effectiveTradeDate']),
      pendingReason: json['pendingReason'] as String?,
    );
  }

  final String id;
  final String requestId;
  final String fundCode;
  final String fundName;
  final String status;
  final Decimal amount;
  final Decimal? shares;
  final DateTime? effectiveTradeDate;
  final String? pendingReason;
}

class ManualSellDraft {
  const ManualSellDraft({
    required this.requestId,
    required this.accountId,
    required this.fundCode,
    required this.sellMode,
    required this.expectedAmount,
    required this.submittedDate,
    required this.submittedPeriod,
    required this.note,
  });

  final String requestId;
  final String accountId;
  final String fundCode;
  final SellMode sellMode;
  final Decimal? expectedAmount;
  final DateTime submittedDate;
  final SubmittedPeriod submittedPeriod;
  final String? note;

  Map<String, Object?> toJson() {
    return {
      'requestId': requestId,
      'accountId': accountId,
      'fundCode': fundCode,
      'sellMode': sellMode.apiValue,
      'expectedAmount': expectedAmount == null
          ? null
          : num.parse(expectedAmount.toString()),
      'actualReceivedAmount': null,
      'submittedDate': _date(submittedDate),
      'submittedPeriod': submittedPeriod.apiValue,
      'confirmedShares': null,
      'confirmedDate': null,
      'note': note,
    };
  }
}

class ManualSellResult {
  const ManualSellResult({
    required this.id,
    required this.requestId,
    required this.fundCode,
    required this.fundName,
    required this.sellMode,
    required this.status,
    required this.expectedAmount,
    required this.shares,
    required this.effectiveTradeDate,
    required this.pendingReason,
  });

  factory ManualSellResult.fromJson(Map<String, dynamic> json) {
    return ManualSellResult(
      id: _requiredString(json, 'id'),
      requestId: _requiredString(json, 'requestId'),
      fundCode: _requiredString(json, 'fundCode'),
      fundName: _requiredString(json, 'fundName'),
      sellMode: _requiredString(json, 'sellMode'),
      status: _requiredString(json, 'status'),
      expectedAmount: _decimalOrNull(json['expectedAmount']),
      shares: _decimalOrNull(json['shares']),
      effectiveTradeDate: _dateOrNull(json['effectiveTradeDate']),
      pendingReason: json['pendingReason'] as String?,
    );
  }

  final String id;
  final String requestId;
  final String fundCode;
  final String fundName;
  final String sellMode;
  final String status;
  final Decimal? expectedAmount;
  final Decimal? shares;
  final DateTime? effectiveTradeDate;
  final String? pendingReason;
}

class ImportIssuePreview {
  const ImportIssuePreview({
    required this.row,
    required this.field,
    required this.code,
    required this.message,
    required this.severity,
  });

  factory ImportIssuePreview.fromJson(Map<String, dynamic> json) {
    return ImportIssuePreview(
      row: (json['row'] as num?)?.toInt(),
      field: json['field'] as String?,
      code: _requiredString(json, 'code'),
      message: _requiredString(json, 'message'),
      severity: _requiredString(json, 'severity'),
    );
  }

  final int? row;
  final String? field;
  final String code;
  final String message;
  final String severity;

  bool get isError => severity == 'ERROR';
}

class ImportRowPreview {
  const ImportRowPreview({
    required this.row,
    required this.rowId,
    required this.fundCode,
    required this.fundName,
    required this.action,
    required this.resultStatus,
    required this.issues,
  });

  factory ImportRowPreview.fromJson(Map<String, dynamic> json) {
    return ImportRowPreview(
      row: (json['row'] as num).toInt(),
      rowId: json['rowId'] as String?,
      fundCode: json['fundCode'] as String?,
      fundName: json['fundName'] as String?,
      action: json['action'] as String?,
      resultStatus:
          json['transactionStatus'] as String? ??
          json['positionStatus'] as String? ??
          json['reviewStatus'] as String?,
      issues: _maps(
        json['issues'],
      ).map(ImportIssuePreview.fromJson).toList(growable: false),
    );
  }

  final int row;
  final String? rowId;
  final String? fundCode;
  final String? fundName;
  final String? action;
  final String? resultStatus;
  final List<ImportIssuePreview> issues;
}

class ImportPreflightResult {
  const ImportPreflightResult({
    required this.batchId,
    required this.status,
    required this.importType,
    required this.accountName,
    required this.accountWillCreate,
    required this.totalCount,
    required this.importableCount,
    required this.warningCount,
    required this.errorCount,
    required this.calibrationCount,
    required this.rows,
    required this.issues,
  });

  factory ImportPreflightResult.fromJson(Map<String, dynamic> json) {
    final account = json['account'] is Map<String, dynamic>
        ? json['account'] as Map<String, dynamic>
        : const <String, dynamic>{};
    return ImportPreflightResult(
      batchId: json['batchId'] as String?,
      status: _requiredString(json, 'status'),
      importType: json['importType'] as String?,
      accountName: account['name'] as String?,
      accountWillCreate: account['willCreate'] as bool? ?? false,
      totalCount: (json['totalCount'] as num?)?.toInt() ?? 0,
      importableCount: (json['importableCount'] as num?)?.toInt() ?? 0,
      warningCount: (json['warningCount'] as num?)?.toInt() ?? 0,
      errorCount: (json['errorCount'] as num?)?.toInt() ?? 0,
      calibrationCount: (json['calibrationCount'] as num?)?.toInt() ?? 0,
      rows: _maps(
        json['rows'],
      ).map(ImportRowPreview.fromJson).toList(growable: false),
      issues: _maps(
        json['issues'],
      ).map(ImportIssuePreview.fromJson).toList(growable: false),
    );
  }

  final String? batchId;
  final String status;
  final String? importType;
  final String? accountName;
  final bool accountWillCreate;
  final int totalCount;
  final int importableCount;
  final int warningCount;
  final int errorCount;
  final int calibrationCount;
  final List<ImportRowPreview> rows;
  final List<ImportIssuePreview> issues;

  bool get canCommit => status == 'READY_TO_COMMIT' && batchId != null;
  bool get isCommitted => status == 'COMMITTED';
}

class ImportCommitResult {
  const ImportCommitResult({
    required this.batchId,
    required this.status,
    required this.accountId,
    required this.accountCreated,
    required this.appliedCount,
    required this.committedAt,
  });

  factory ImportCommitResult.fromJson(Map<String, dynamic> json) {
    final applied =
        (json['importedCount'] as num?)?.toInt() ??
        (json['appliedCount'] as num?)?.toInt() ??
        0;
    return ImportCommitResult(
      batchId: _requiredString(json, 'batchId'),
      status: _requiredString(json, 'status'),
      accountId: _requiredString(json, 'accountId'),
      accountCreated: json['accountCreated'] as bool? ?? false,
      appliedCount: applied + ((json['clearedCount'] as num?)?.toInt() ?? 0),
      committedAt: _dateOrNull(json['committedAt']),
    );
  }

  final String batchId;
  final String status;
  final String accountId;
  final bool accountCreated;
  final int appliedCount;
  final DateTime? committedAt;
}

String _date(DateTime value) {
  String two(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${two(value.month)}-${two(value.day)}';
}

DateTime? _dateOrNull(Object? value) {
  return value is String ? DateTime.tryParse(value) : null;
}

Decimal _decimal(Object? value) {
  if (value is num || value is String) {
    return Decimal.parse(value.toString());
  }
  throw const FormatException('Expected a decimal value.');
}

Decimal? _decimalOrNull(Object? value) {
  return value == null ? null : _decimal(value);
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.isNotEmpty) {
    return value;
  }
  throw FormatException('Missing required string: $key');
}

List<Map<String, dynamic>> _maps(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<Map<Object?, Object?>>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}
