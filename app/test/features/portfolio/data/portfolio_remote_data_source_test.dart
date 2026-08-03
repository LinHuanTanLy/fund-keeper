import 'dart:convert';
import 'dart:typed_data';

import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/portfolio/data/portfolio_remote_data_source.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

void main() {
  test(
    'uses account filters and preserves unavailable valuation nulls',
    () async {
      final adapter = _PortfolioContractAdapter();
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.example.test',
          responseType: ResponseType.json,
        ),
      )..httpClientAdapter = adapter;
      final remote = DioPortfolioRemoteDataSource(dio);

      final accounts = await remote.listAccounts();
      final overview = await remote.getOverview('account-active');
      final funds = await remote.listFunds('account-active');
      final detail = await remote.getFundDetail('012345');

      expect(accounts.map((account) => account.id), ['account-active']);
      expect(adapter.requests[1].queryParameters, {
        'accountId': 'account-active',
      });
      expect(adapter.requests[2].queryParameters, {
        'accountId': 'account-active',
      });
      expect(overview.currentMarketValue, isNull);
      expect(overview.todayEstimatedProfit, isNull);
      expect(overview.totalHoldingCost, Decimal.parse('1000.00'));
      expect(overview.valuationStatus, PortfolioValuationStatus.unavailable);
      expect(funds.single.primaryTheme, FundPrimaryTheme.semiconductor);
      expect(funds.single.currentMarketValue, isNull);
      expect(adapter.requests[3].path, '/api/v1/portfolio/funds/012345');
      expect(adapter.requests[3].queryParameters, {'page': 0, 'size': 20});
      expect(detail.summary.fundCode, '012345');
      expect(detail.accounts.single.accountName, '默认账户');
      expect(detail.accounts.single.unitNav, Decimal.parse('1.25'));
      expect(detail.openTransactions.single.status, 'PENDING');
      expect(detail.transactions.totalElements, 1);

      dio.close(force: true);
    },
  );
}

class _PortfolioContractAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    switch (options.path) {
      case '/api/v1/accounts':
        return _jsonResponse({
          'code': 'OK',
          'data': [
            {
              'id': 'account-active',
              'name': '默认账户',
              'platform': 'OTHER',
              'status': 'ACTIVE',
            },
            {
              'id': 'account-archived',
              'name': '历史账户',
              'platform': 'OTHER',
              'status': 'ARCHIVED',
            },
          ],
        });
      case '/api/v1/portfolio/overview':
        return _jsonResponse({'code': 'OK', 'data': _overviewJson()});
      case '/api/v1/portfolio/funds':
        return _jsonResponse({
          'code': 'OK',
          'data': [_fundJson()],
        });
      case '/api/v1/portfolio/funds/012345':
        return _jsonResponse({'code': 'OK', 'data': _detailJson()});
      default:
        return _jsonResponse({
          'code': 'NOT_FOUND',
          'data': null,
        }, statusCode: 404);
    }
  }

  Map<String, Object?> _overviewJson() {
    return {
      'positionCount': 1,
      'valuedPositionCount': 0,
      'missingValuationCount': 1,
      'totalHoldingCost': 1000.00,
      'valuedHoldingCost': 0,
      'currentMarketValue': null,
      'currentHoldingProfit': null,
      'currentHoldingReturnPercent': null,
      'realizedProfit': 0,
      'cumulativeProfit': null,
      'returnCostBasis': null,
      'cumulativeReturnPercent': null,
      'todayEstimatedProfit': null,
      'todayEstimateComplete': false,
      'confirmedSellCount': 0,
      'openSellCount': 0,
      'containsEstimatedData': false,
      'valuationComplete': false,
      'valuationStatus': 'UNAVAILABLE',
      'priceType': null,
      'dataDate': null,
      'observedAt': null,
      'holdingStartDate': '2026-07-20',
      'holdingDays': 8,
    };
  }

  Map<String, Object?> _fundJson() {
    return {
      'fundCode': '012345',
      'fundName': '某半导体基金',
      'primaryTheme': 'SEMICONDUCTOR',
      'hasCurrentPosition': true,
      'accountCount': 1,
      'totalShares': 800,
      'pendingBuyAmount': null,
      'openTransactionCount': 0,
      'holdingCost': 1000,
      'currentMarketValue': null,
      'currentHoldingProfit': null,
      'currentHoldingReturnPercent': null,
      'realizedProfit': 0,
      'cumulativeProfit': null,
      'todayEstimatedProfit': null,
      'todayEstimateComplete': false,
      'openSellCount': 0,
      'containsEstimatedData': false,
      'valuationComplete': false,
      'valuationStatus': 'UNAVAILABLE',
      'priceType': null,
      'dataDate': null,
      'observedAt': null,
      'holdingStartDate': '2026-07-20',
      'holdingDays': 8,
    };
  }

  Map<String, Object?> _detailJson() {
    final transaction = {
      'id': 'transaction-1',
      'requestId': 'request-1',
      'accountId': 'account-active',
      'accountName': '默认账户',
      'fundCode': '012345',
      'fundName': '某半导体基金',
      'type': 'BUY',
      'sellMode': null,
      'status': 'PENDING',
      'amount': 1000,
      'expectedAmount': null,
      'actualReceivedAmount': null,
      'removedCost': null,
      'realizedProfit': null,
      'shares': null,
      'submittedDate': '2026-07-20',
      'submittedPeriod': 'BEFORE_15',
      'effectiveTradeDate': '2026-07-20',
      'confirmedDate': null,
      'pendingReason': 'WAITING_NAV',
      'cancellationReason': null,
      'createdAt': '2026-07-20T02:00:00Z',
    };
    return {
      'summary': _fundJson(),
      'accounts': [
        {
          'positionId': 'position-1',
          'accountId': 'account-active',
          'accountName': '默认账户',
          'accountPlatform': 'OTHER',
          'shares': 800,
          'holdingCost': 1000,
          'currentMarketValue': null,
          'currentHoldingProfit': null,
          'currentHoldingReturnPercent': null,
          'realizedProfit': 0,
          'cumulativeProfit': null,
          'todayEstimatedProfit': null,
          'openSellCount': 0,
          'positionStatus': 'ESTIMATED',
          'valuationStatus': 'OFFICIAL',
          'priceType': 'OFFICIAL',
          'unitNav': 1.25,
          'estimatedChangePercent': null,
          'baseNavDate': '2026-07-19',
          'baseNav': 1.24,
          'dataDate': '2026-07-20',
          'observedAt': null,
          'dataSource': 'eastmoney-public',
          'holdingStartDate': '2026-07-20',
          'holdingDays': 8,
        },
      ],
      'openTransactions': [transaction],
      'transactions': {
        'items': [transaction],
        'page': 0,
        'size': 20,
        'totalElements': 1,
        'totalPages': 1,
      },
    };
  }

  ResponseBody _jsonResponse(
    Map<String, Object?> body, {
    int statusCode = 200,
  }) {
    return ResponseBody.fromString(
      jsonEncode(body),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
