import 'package:fund_keeper/features/portfolio/data/portfolio_remote_data_source.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

class FakePortfolioRemoteDataSource implements PortfolioRemoteDataSource {
  FakePortfolioRemoteDataSource({
    List<PortfolioAccount>? accounts,
    PortfolioOverview? overview,
    List<FundPortfolioCard>? funds,
    FundPortfolioDetail? detail,
  }) : accounts = accounts ?? const [],
       overview = overview ?? portfolioOverviewFixture(),
       funds = funds ?? const [],
       detail = detail ?? fundPortfolioDetailFixture();

  List<PortfolioAccount> accounts;
  PortfolioOverview overview;
  List<FundPortfolioCard> funds;
  FundPortfolioDetail detail;
  int overviewCalls = 0;
  int fundCalls = 0;
  String? lastOverviewAccountId;
  String? lastFundsAccountId;
  String? lastDetailFundCode;

  @override
  Future<FundPortfolioDetail> getFundDetail(
    String fundCode, {
    int page = 0,
    int size = 20,
  }) async {
    lastDetailFundCode = fundCode;
    return detail;
  }

  @override
  Future<PortfolioOverview> getOverview(String? accountId) async {
    overviewCalls += 1;
    lastOverviewAccountId = accountId;
    return overview;
  }

  @override
  Future<List<PortfolioAccount>> listAccounts() async => accounts;

  @override
  Future<List<FundPortfolioCard>> listFunds(String? accountId) async {
    fundCalls += 1;
    lastFundsAccountId = accountId;
    return funds;
  }
}

PortfolioOverview portfolioOverviewFixture({
  bool valuationComplete = true,
  bool todayEstimateComplete = true,
  Object? currentMarketValue = 3000,
  Object? cumulativeProfit = 300,
  Object? todayEstimatedProfit = 30,
  int positionCount = 2,
  String valuationStatus = 'LIVE',
  String priceType = 'MARKET',
}) {
  return PortfolioOverview.fromJson({
    'positionCount': positionCount,
    'valuedPositionCount': valuationComplete ? positionCount : 0,
    'missingValuationCount': valuationComplete ? 0 : positionCount,
    'totalHoldingCost': 2700,
    'valuedHoldingCost': valuationComplete ? 2700 : 0,
    'currentMarketValue': currentMarketValue,
    'currentHoldingProfit': cumulativeProfit,
    'currentHoldingReturnPercent': 11.11,
    'realizedProfit': 0,
    'cumulativeProfit': cumulativeProfit,
    'returnCostBasis': 2700,
    'cumulativeReturnPercent': 11.11,
    'todayEstimatedProfit': todayEstimatedProfit,
    'todayEstimateComplete': todayEstimateComplete,
    'confirmedSellCount': 0,
    'openSellCount': 0,
    'containsEstimatedData': false,
    'valuationComplete': valuationComplete,
    'valuationStatus': valuationComplete ? valuationStatus : 'UNAVAILABLE',
    'priceType': valuationComplete ? priceType : null,
    'dataDate': '2026-07-27',
    'observedAt': valuationComplete ? '2026-07-27T07:30:00Z' : null,
    'holdingStartDate': '2026-07-01',
    'holdingDays': 27,
  });
}

FundPortfolioCard fundCardFixture({
  required String code,
  required String name,
  required String theme,
  bool hasCurrentPosition = true,
  Object? totalShares = 800,
  int openSellCount = 0,
  Object? marketValue = 1000,
  Object? holdingCost = 900,
  Object? holdingProfit = 100,
  Object? todayProfit = 10,
  int holdingDays = 20,
  String valuationStatus = 'LIVE',
  String priceType = 'MARKET',
  Object? observedAt = '2026-07-27T07:30:00Z',
}) {
  return FundPortfolioCard.fromJson({
    'fundCode': code,
    'fundName': name,
    'primaryTheme': theme,
    'hasCurrentPosition': hasCurrentPosition,
    'accountCount': 1,
    'totalShares': totalShares,
    'pendingBuyAmount': null,
    'openTransactionCount': 0,
    'holdingCost': holdingCost,
    'currentMarketValue': marketValue,
    'currentHoldingProfit': holdingProfit,
    'currentHoldingReturnPercent': marketValue == null ? null : 11.11,
    'realizedProfit': 0,
    'cumulativeProfit': holdingProfit,
    'todayEstimatedProfit': todayProfit,
    'todayEstimateComplete': todayProfit != null,
    'openSellCount': openSellCount,
    'containsEstimatedData': false,
    'valuationComplete': marketValue != null,
    'valuationStatus': marketValue == null ? 'UNAVAILABLE' : valuationStatus,
    'priceType': marketValue == null ? null : priceType,
    'dataDate': '2026-07-27',
    'observedAt': marketValue == null ? null : observedAt,
    'holdingStartDate': '2026-07-08',
    'holdingDays': holdingDays,
  });
}

FundPortfolioDetail fundPortfolioDetailFixture() {
  final summary = fundCardFixture(
    code: '510300',
    name: '沪深300ETF',
    theme: 'BROAD_INDEX',
    marketValue: 5200,
    holdingCost: 5000,
    holdingProfit: 200,
    todayProfit: 35,
  );
  return FundPortfolioDetail.fromJson({
    'summary': summary.toJson(),
    'accounts': [
      {
        'positionId': 'position-1',
        'accountId': 'account-1',
        'accountName': '默认账户',
        'accountPlatform': 'OTHER',
        'shares': 1300,
        'holdingCost': 5000,
        'currentMarketValue': 5200,
        'currentHoldingProfit': 200,
        'currentHoldingReturnPercent': 4,
        'realizedProfit': 0,
        'cumulativeProfit': 200,
        'todayEstimatedProfit': 35,
        'openSellCount': 0,
        'positionStatus': 'CONFIRMED',
        'valuationStatus': 'LIVE',
        'priceType': 'MARKET',
        'unitNav': 4,
        'estimatedChangePercent': 0.68,
        'baseNavDate': '2026-08-02',
        'baseNav': 3.97,
        'dataDate': '2026-08-03',
        'observedAt': '2026-08-03T02:30:00Z',
        'dataSource': 'eastmoney-market',
        'holdingStartDate': '2026-07-01',
        'holdingDays': 34,
      },
    ],
    'openTransactions': <Object?>[],
    'transactions': {
      'items': [
        {
          'id': 'transaction-1',
          'requestId': 'request-1',
          'accountId': 'account-1',
          'accountName': '默认账户',
          'fundCode': '510300',
          'fundName': '沪深300ETF',
          'type': 'BUY',
          'sellMode': null,
          'status': 'CONFIRMED',
          'amount': 5000,
          'expectedAmount': null,
          'actualReceivedAmount': null,
          'removedCost': null,
          'realizedProfit': null,
          'shares': 1300,
          'submittedDate': '2026-07-01',
          'submittedPeriod': 'BEFORE_15',
          'effectiveTradeDate': '2026-07-01',
          'confirmedDate': '2026-07-01',
          'pendingReason': null,
          'cancellationReason': null,
          'createdAt': '2026-07-01T03:00:00Z',
        },
      ],
      'page': 0,
      'size': 20,
      'totalElements': 1,
      'totalPages': 1,
    },
  });
}
