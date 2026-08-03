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
