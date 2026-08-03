import 'package:fund_keeper/features/portfolio/data/portfolio_remote_data_source.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

class FakePortfolioRemoteDataSource implements PortfolioRemoteDataSource {
  FakePortfolioRemoteDataSource({
    List<PortfolioAccount>? accounts,
    PortfolioOverview? overview,
    List<FundPortfolioCard>? funds,
  }) : accounts = accounts ?? const [],
       overview = overview ?? portfolioOverviewFixture(),
       funds = funds ?? const [];

  List<PortfolioAccount> accounts;
  PortfolioOverview overview;
  List<FundPortfolioCard> funds;
  int overviewCalls = 0;
  int fundCalls = 0;
  String? lastOverviewAccountId;
  String? lastFundsAccountId;

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
