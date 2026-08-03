import 'dart:convert';
import 'dart:typed_data';

import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/transaction/data/transaction_remote_data_source.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';

void main() {
  test('sends all transaction filters and parses stable pagination', () async {
    final adapter = _TransactionContractAdapter();
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioTransactionRemoteDataSource(dio);

    final page = await remote.listTransactions(
      filters: const TransactionFilters(
        accountId: 'account-1',
        fundCode: '005827',
        type: 'SELL',
        status: 'CONFIRMED',
      ),
      page: 2,
      size: 20,
    );

    expect(page.page, 2);
    expect(page.totalPages, 3);
    expect(page.items.single.id, 'transaction-1');
    expect(adapter.requests.single.queryParameters, {
      'accountId': 'account-1',
      'fundCode': '005827',
      'type': 'SELL',
      'status': 'CONFIRMED',
      'page': 2,
      'size': 20,
    });
    dio.close(force: true);
  });

  test(
    'confirmation timeout succeeds only after querying confirmed state',
    () async {
      final adapter = _TransactionContractAdapter(
        timeoutPath: '/api/v1/transactions/transaction-1/buy-confirmation',
        recoveryStatus: 'CONFIRMED',
      );
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.example.test',
          responseType: ResponseType.json,
        ),
      )..httpClientAdapter = adapter;
      final remote = DioTransactionRemoteDataSource(dio);

      final result = await remote.confirmBuy(
        'transaction-1',
        BuyConfirmationDraft(
          confirmedShares: Decimal.parse('486.12345678'),
          confirmedDate: DateTime(2026, 7, 27),
        ),
      );

      expect(result.status, 'CONFIRMED');
      expect(adapter.requests.map((request) => request.path), [
        '/api/v1/transactions/transaction-1/buy-confirmation',
        '/api/v1/transactions/transaction-1',
      ]);
      expect(adapter.requests.first.data, {
        'confirmedShares': 486.12345678,
        'confirmedDate': '2026-07-27',
      });
      dio.close(force: true);
    },
  );

  test(
    'partial sell confirmation and cancellation use exact contracts',
    () async {
      final adapter = _TransactionContractAdapter();
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.example.test',
          responseType: ResponseType.json,
        ),
      )..httpClientAdapter = adapter;
      final remote = DioTransactionRemoteDataSource(dio);

      await remote.confirmSell(
        'transaction-1',
        SellConfirmationDraft(
          actualReceivedAmount: Decimal.parse('798.50'),
          confirmedShares: Decimal.parse('320.12345678'),
          confirmedDate: DateTime(2026, 7, 27),
        ),
      );
      await remote.cancel('transaction-2', '平台最终未成交');

      expect(adapter.requests[0].path, endsWith('/sell-confirmation'));
      expect(adapter.requests[0].data, {
        'actualReceivedAmount': 798.5,
        'confirmedShares': 320.12345678,
        'confirmedDate': '2026-07-27',
      });
      expect(adapter.requests[1].path, endsWith('/transaction-2/cancel'));
      expect(adapter.requests[1].data, {'reason': '平台最终未成交'});
      dio.close(force: true);
    },
  );
}

class _TransactionContractAdapter implements HttpClientAdapter {
  _TransactionContractAdapter({
    this.timeoutPath,
    this.recoveryStatus = 'ESTIMATED',
  });

  final String? timeoutPath;
  final String recoveryStatus;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (options.path == timeoutPath) {
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
      );
    }
    if (options.path == '/api/v1/transactions') {
      return _jsonResponse({
        'items': [_transactionJson(status: 'CONFIRMED')],
        'page': 2,
        'size': 20,
        'totalElements': 41,
        'totalPages': 3,
      });
    }
    return _jsonResponse(_transactionJson(status: recoveryStatus));
  }

  Map<String, Object?> _transactionJson({required String status}) {
    return {
      'id': 'transaction-1',
      'requestId': 'request-1',
      'accountId': 'account-1',
      'accountName': '默认账户',
      'fundCode': '005827',
      'fundName': '易方达蓝筹精选混合',
      'type': 'BUY',
      'sellMode': null,
      'status': status,
      'amount': 1000,
      'expectedAmount': null,
      'actualReceivedAmount': null,
      'removedCost': null,
      'realizedProfit': null,
      'shares': 486.12345678,
      'submittedDate': '2026-07-27',
      'submittedPeriod': 'BEFORE_15',
      'effectiveTradeDate': '2026-07-27',
      'confirmedDate': status == 'CONFIRMED' ? '2026-07-27' : null,
      'pendingReason': null,
      'cancellationReason': null,
      'createdAt': '2026-07-27T08:00:00Z',
    };
  }

  ResponseBody _jsonResponse(Object? data) {
    return ResponseBody.fromString(
      jsonEncode({'code': 'OK', 'message': 'success', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
