import 'dart:convert';
import 'dart:typed_data';

import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/entry/data/entry_remote_data_source.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';

void main() {
  test('manual buy queries the idempotency result after a timeout', () async {
    final adapter = _EntryContractAdapter(timeoutManualBuy: true);
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioEntryRemoteDataSource(dio);
    final draft = ManualBuyDraft(
      requestId: 'buy-timeout-001',
      accountId: 'account-1',
      fundCode: '005827',
      amount: Decimal.parse('1000.50'),
      submittedDate: DateTime(2026, 7, 27),
      submittedPeriod: SubmittedPeriod.after15,
    );

    final result = await remote.createManualBuy(draft);

    expect(result.requestId, 'buy-timeout-001');
    expect(adapter.requests.map((request) => request.path), [
      '/api/v1/transactions/buys',
      '/api/v1/transactions/requests/buy-timeout-001',
    ]);
    expect(adapter.requests.first.data, {
      'requestId': 'buy-timeout-001',
      'accountId': 'account-1',
      'fundCode': '005827',
      'amount': 1000.5,
      'submittedDate': '2026-07-27',
      'submittedPeriod': 'AFTER_15',
      'confirmedShares': null,
      'confirmedDate': null,
      'note': 'Flutter 手动录入',
    });
    dio.close(force: true);
  });

  test(
    'manual partial sell queries its idempotency result after timeout',
    () async {
      final adapter = _EntryContractAdapter(timeoutManualSell: true);
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.example.test',
          responseType: ResponseType.json,
        ),
      )..httpClientAdapter = adapter;
      final remote = DioEntryRemoteDataSource(dio);
      final draft = ManualSellDraft(
        requestId: 'sell-timeout-001',
        accountId: 'account-1',
        fundCode: '005827',
        sellMode: SellMode.partial,
        expectedAmount: Decimal.parse('800.50'),
        submittedDate: DateTime(2026, 7, 27),
        submittedPeriod: SubmittedPeriod.before15,
        note: '部分卖出',
      );

      final result = await remote.createManualSell(draft);

      expect(result.requestId, 'sell-timeout-001');
      expect(result.sellMode, 'PARTIAL');
      expect(adapter.requests.map((request) => request.path), [
        '/api/v1/transactions/sells',
        '/api/v1/transactions/requests/sell-timeout-001',
      ]);
      expect(adapter.requests.first.data, {
        'requestId': 'sell-timeout-001',
        'accountId': 'account-1',
        'fundCode': '005827',
        'sellMode': 'PARTIAL',
        'expectedAmount': 800.5,
        'actualReceivedAmount': null,
        'submittedDate': '2026-07-27',
        'submittedPeriod': 'BEFORE_15',
        'confirmedShares': null,
        'confirmedDate': null,
        'note': '部分卖出',
      });
      dio.close(force: true);
    },
  );

  test('routes raw JSON and commit by the explicitly selected kind', () async {
    final adapter = _EntryContractAdapter();
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioEntryRemoteDataSource(dio);
    const raw = '{"importType":"POSITION_SNAPSHOT"}';

    final preflight = await remote.preflightImport(
      ImportKind.positionSnapshot,
      raw,
    );
    final committed = await remote.commitImport(
      ImportKind.positionSnapshot,
      preflight.batchId!,
    );

    expect(preflight.canCommit, isTrue);
    expect(committed.status, 'COMMITTED');
    expect(adapter.requests[0].path, contains('position-snapshots/preflight'));
    expect(adapter.requests[0].data, raw);
    expect(
      adapter.requests[0].contentType,
      startsWith(Headers.jsonContentType),
    );
    expect(
      adapter.requests[1].path,
      '/api/v1/imports/position-snapshots/batch-001/commit',
    );
    dio.close(force: true);
  });

  test('parses syntax-error preflight without a batch id', () {
    final result = ImportPreflightResult.fromJson({
      'batchId': null,
      'status': 'PREFLIGHT_FAILED',
      'schemaVersion': null,
      'importType': null,
      'account': null,
      'totalCount': 0,
      'importableCount': 0,
      'warningCount': 0,
      'errorCount': 1,
      'rows': <Object?>[],
      'issues': [
        {
          'row': null,
          'field': r'$',
          'code': 'JSON_SYNTAX_ERROR',
          'message': 'JSON 语法不正确',
          'severity': 'ERROR',
        },
      ],
    });

    expect(result.batchId, isNull);
    expect(result.canCommit, isFalse);
    expect(result.issues.single.code, 'JSON_SYNTAX_ERROR');
  });
}

class _EntryContractAdapter implements HttpClientAdapter {
  _EntryContractAdapter({
    this.timeoutManualBuy = false,
    this.timeoutManualSell = false,
  });

  final bool timeoutManualBuy;
  final bool timeoutManualSell;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (options.path == '/api/v1/transactions/buys' && timeoutManualBuy) {
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
      );
    }
    if (options.path == '/api/v1/transactions/sells' && timeoutManualSell) {
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
      );
    }
    if (options.path.startsWith('/api/v1/transactions/')) {
      final isSell =
          options.path == '/api/v1/transactions/sells' ||
          options.path.contains('sell-timeout');
      return _jsonResponse(isSell ? _manualSellData() : _manualBuyData());
    }
    if (options.path.endsWith('/preflight')) {
      return _jsonResponse(_preflightData());
    }
    if (options.path.endsWith('/commit')) {
      return _jsonResponse(_commitData(), statusCode: 201);
    }
    return _jsonResponse(null, statusCode: 404, code: 'NOT_FOUND');
  }

  Map<String, Object?> _manualBuyData() {
    return {
      'id': 'transaction-1',
      'requestId': 'buy-timeout-001',
      'accountId': 'account-1',
      'accountName': '默认账户',
      'fundCode': '005827',
      'fundName': '易方达蓝筹精选混合',
      'type': 'BUY',
      'status': 'ESTIMATED',
      'amount': 1000.5,
      'shares': 486.12345678,
      'effectiveTradeDate': '2026-07-28',
      'pendingReason': null,
    };
  }

  Map<String, Object?> _manualSellData() {
    return {
      'id': 'sell-transaction-1',
      'requestId': 'sell-timeout-001',
      'accountId': 'account-1',
      'accountName': '默认账户',
      'fundCode': '005827',
      'fundName': '易方达蓝筹精选混合',
      'type': 'SELL',
      'sellMode': 'PARTIAL',
      'status': 'ESTIMATED',
      'expectedAmount': 800.5,
      'actualReceivedAmount': null,
      'shares': 388.12345678,
      'effectiveTradeDate': '2026-07-27',
      'pendingReason': null,
    };
  }

  Map<String, Object?> _preflightData() {
    return {
      'batchId': 'batch-001',
      'status': 'READY_TO_COMMIT',
      'schemaVersion': '1.0',
      'importType': 'POSITION_SNAPSHOT',
      'account': {
        'accountId': 'account-1',
        'name': '默认账户',
        'platform': 'OTHER',
        'willCreate': false,
      },
      'totalCount': 1,
      'importableCount': 1,
      'warningCount': 0,
      'errorCount': 0,
      'calibrationCount': 0,
      'rows': [
        {
          'row': 1,
          'fundCode': '005827',
          'fundName': '易方达蓝筹精选混合',
          'action': 'ADD',
          'positionStatus': 'ESTIMATED',
          'issues': <Object?>[],
        },
      ],
      'issues': <Object?>[],
    };
  }

  Map<String, Object?> _commitData() {
    return {
      'batchId': 'batch-001',
      'status': 'COMMITTED',
      'accountId': 'account-1',
      'accountCreated': false,
      'appliedCount': 1,
      'clearedCount': 0,
      'rows': [],
      'committedAt': '2026-07-27T08:00:00Z',
    };
  }

  ResponseBody _jsonResponse(
    Object? data, {
    int statusCode = 200,
    String code = 'OK',
  }) {
    return ResponseBody.fromString(
      jsonEncode({'code': code, 'message': 'success', 'data': data}),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
