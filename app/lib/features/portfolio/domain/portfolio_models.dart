import 'package:decimal/decimal.dart';
import 'package:fund_keeper/core/network/decimal_json_converter.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';
import 'package:json_annotation/json_annotation.dart';

part 'portfolio_models.g.dart';

enum FundPrimaryTheme {
  @JsonValue('SEMICONDUCTOR')
  semiconductor,
  @JsonValue('INTERNET')
  internet,
  @JsonValue('CONSUMER')
  consumer,
  @JsonValue('HEALTHCARE')
  healthcare,
  @JsonValue('NEW_ENERGY')
  newEnergy,
  @JsonValue('BROAD_INDEX')
  broadIndex,
  @JsonValue('FINANCE')
  finance,
  @JsonValue('OVERSEAS')
  overseas,
  @JsonValue('MIXED')
  mixed,
  @JsonValue('OTHER')
  other,
}

enum PortfolioValuationStatus {
  @JsonValue('LIVE')
  live,
  @JsonValue('DELAYED')
  delayed,
  @JsonValue('STALE')
  stale,
  @JsonValue('OFFICIAL')
  official,
  @JsonValue('MARKET_CLOSED')
  marketClosed,
  @JsonValue('UNAVAILABLE')
  unavailable,
}

enum PortfolioPriceType {
  @JsonValue('MARKET')
  market,
  @JsonValue('ESTIMATED')
  estimated,
  @JsonValue('OFFICIAL')
  official,
  @JsonValue('MIXED')
  mixed,
  unknown,
}

@JsonSerializable()
class PortfolioAccount {
  const PortfolioAccount({
    required this.id,
    required this.name,
    required this.platform,
    required this.status,
  });

  factory PortfolioAccount.fromJson(Map<String, dynamic> json) {
    return _$PortfolioAccountFromJson(json);
  }

  final String id;
  final String name;
  final String platform;
  final String status;

  Map<String, dynamic> toJson() => _$PortfolioAccountToJson(this);
}

@JsonSerializable()
class PortfolioOverview {
  const PortfolioOverview({
    required this.positionCount,
    required this.valuedPositionCount,
    required this.missingValuationCount,
    required this.totalHoldingCost,
    required this.valuedHoldingCost,
    required this.currentMarketValue,
    required this.currentHoldingProfit,
    required this.currentHoldingReturnPercent,
    required this.realizedProfit,
    required this.cumulativeProfit,
    required this.returnCostBasis,
    required this.cumulativeReturnPercent,
    required this.todayEstimatedProfit,
    required this.todayEstimateComplete,
    required this.confirmedSellCount,
    required this.openSellCount,
    required this.containsEstimatedData,
    required this.valuationComplete,
    required this.valuationStatus,
    required this.priceType,
    required this.dataDate,
    required this.observedAt,
    required this.holdingStartDate,
    required this.holdingDays,
  });

  factory PortfolioOverview.fromJson(Map<String, dynamic> json) {
    return _$PortfolioOverviewFromJson(json);
  }

  final int positionCount;
  final int valuedPositionCount;
  final int missingValuationCount;
  @DecimalJsonConverter()
  final Decimal totalHoldingCost;
  @DecimalJsonConverter()
  final Decimal valuedHoldingCost;
  @NullableDecimalJsonConverter()
  final Decimal? currentMarketValue;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingProfit;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingReturnPercent;
  @DecimalJsonConverter()
  final Decimal realizedProfit;
  @NullableDecimalJsonConverter()
  final Decimal? cumulativeProfit;
  @NullableDecimalJsonConverter()
  final Decimal? returnCostBasis;
  @NullableDecimalJsonConverter()
  final Decimal? cumulativeReturnPercent;
  @NullableDecimalJsonConverter()
  final Decimal? todayEstimatedProfit;
  final bool todayEstimateComplete;
  final int confirmedSellCount;
  final int openSellCount;
  final bool containsEstimatedData;
  final bool valuationComplete;
  @JsonKey(unknownEnumValue: PortfolioValuationStatus.unavailable)
  final PortfolioValuationStatus? valuationStatus;
  @JsonKey(unknownEnumValue: PortfolioPriceType.unknown)
  final PortfolioPriceType? priceType;
  final DateTime? dataDate;
  final DateTime? observedAt;
  final DateTime? holdingStartDate;
  final int? holdingDays;

  Map<String, dynamic> toJson() => _$PortfolioOverviewToJson(this);
}

@JsonSerializable()
class FundPortfolioCard {
  const FundPortfolioCard({
    required this.fundCode,
    required this.fundName,
    required this.primaryTheme,
    required this.hasCurrentPosition,
    required this.accountCount,
    required this.totalShares,
    required this.pendingBuyAmount,
    required this.openTransactionCount,
    required this.holdingCost,
    required this.currentMarketValue,
    required this.currentHoldingProfit,
    required this.currentHoldingReturnPercent,
    required this.realizedProfit,
    required this.cumulativeProfit,
    required this.todayEstimatedProfit,
    required this.todayEstimateComplete,
    required this.openSellCount,
    required this.containsEstimatedData,
    required this.valuationComplete,
    required this.valuationStatus,
    required this.priceType,
    required this.dataDate,
    required this.observedAt,
    required this.holdingStartDate,
    required this.holdingDays,
  });

  factory FundPortfolioCard.fromJson(Map<String, dynamic> json) {
    return _$FundPortfolioCardFromJson(json);
  }

  final String fundCode;
  final String fundName;
  @JsonKey(unknownEnumValue: FundPrimaryTheme.other)
  final FundPrimaryTheme primaryTheme;
  final bool hasCurrentPosition;
  final int accountCount;
  @NullableDecimalJsonConverter()
  final Decimal? totalShares;
  @NullableDecimalJsonConverter()
  final Decimal? pendingBuyAmount;
  final int openTransactionCount;
  @NullableDecimalJsonConverter()
  final Decimal? holdingCost;
  @NullableDecimalJsonConverter()
  final Decimal? currentMarketValue;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingProfit;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingReturnPercent;
  @DecimalJsonConverter()
  final Decimal realizedProfit;
  @NullableDecimalJsonConverter()
  final Decimal? cumulativeProfit;
  @NullableDecimalJsonConverter()
  final Decimal? todayEstimatedProfit;
  final bool todayEstimateComplete;
  final int openSellCount;
  final bool containsEstimatedData;
  final bool valuationComplete;
  @JsonKey(unknownEnumValue: PortfolioValuationStatus.unavailable)
  final PortfolioValuationStatus? valuationStatus;
  @JsonKey(unknownEnumValue: PortfolioPriceType.unknown)
  final PortfolioPriceType? priceType;
  final DateTime? dataDate;
  final DateTime? observedAt;
  final DateTime? holdingStartDate;
  final int? holdingDays;

  Map<String, dynamic> toJson() => _$FundPortfolioCardToJson(this);
}

class PortfolioHomeData {
  const PortfolioHomeData({required this.overview, required this.funds});

  final PortfolioOverview overview;
  final List<FundPortfolioCard> funds;
}

@JsonSerializable()
class FundPortfolioAccount {
  const FundPortfolioAccount({
    required this.positionId,
    required this.accountId,
    required this.accountName,
    required this.accountPlatform,
    required this.shares,
    required this.holdingCost,
    required this.currentMarketValue,
    required this.currentHoldingProfit,
    required this.currentHoldingReturnPercent,
    required this.realizedProfit,
    required this.cumulativeProfit,
    required this.todayEstimatedProfit,
    required this.openSellCount,
    required this.positionStatus,
    required this.valuationStatus,
    required this.priceType,
    required this.unitNav,
    required this.estimatedChangePercent,
    required this.baseNavDate,
    required this.baseNav,
    required this.dataDate,
    required this.observedAt,
    required this.dataSource,
    required this.holdingStartDate,
    required this.holdingDays,
  });

  factory FundPortfolioAccount.fromJson(Map<String, dynamic> json) {
    return _$FundPortfolioAccountFromJson(json);
  }

  final String positionId;
  final String accountId;
  final String accountName;
  final String accountPlatform;
  @NullableDecimalJsonConverter()
  final Decimal? shares;
  @NullableDecimalJsonConverter()
  final Decimal? holdingCost;
  @NullableDecimalJsonConverter()
  final Decimal? currentMarketValue;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingProfit;
  @NullableDecimalJsonConverter()
  final Decimal? currentHoldingReturnPercent;
  @DecimalJsonConverter()
  final Decimal realizedProfit;
  @NullableDecimalJsonConverter()
  final Decimal? cumulativeProfit;
  @NullableDecimalJsonConverter()
  final Decimal? todayEstimatedProfit;
  final int openSellCount;
  final String positionStatus;
  @JsonKey(unknownEnumValue: PortfolioValuationStatus.unavailable)
  final PortfolioValuationStatus? valuationStatus;
  @JsonKey(unknownEnumValue: PortfolioPriceType.unknown)
  final PortfolioPriceType? priceType;
  @NullableDecimalJsonConverter()
  final Decimal? unitNav;
  @NullableDecimalJsonConverter()
  final Decimal? estimatedChangePercent;
  final DateTime? baseNavDate;
  @NullableDecimalJsonConverter()
  final Decimal? baseNav;
  final DateTime? dataDate;
  final DateTime? observedAt;
  final String? dataSource;
  final DateTime? holdingStartDate;
  final int? holdingDays;

  Map<String, dynamic> toJson() => _$FundPortfolioAccountToJson(this);
}

class FundPortfolioDetail {
  const FundPortfolioDetail({
    required this.summary,
    required this.accounts,
    required this.openTransactions,
    required this.transactions,
  });

  factory FundPortfolioDetail.fromJson(Map<String, dynamic> json) {
    return FundPortfolioDetail(
      summary: FundPortfolioCard.fromJson(_requiredMap(json['summary'])),
      accounts: _requiredList(json['accounts'])
          .map((item) => FundPortfolioAccount.fromJson(_requiredMap(item)))
          .toList(growable: false),
      openTransactions: _requiredList(json['openTransactions'])
          .map((item) => TransactionRecord.fromJson(_requiredMap(item)))
          .toList(growable: false),
      transactions: TransactionPage.fromJson(
        _requiredMap(json['transactions']),
      ),
    );
  }

  final FundPortfolioCard summary;
  final List<FundPortfolioAccount> accounts;
  final List<TransactionRecord> openTransactions;
  final TransactionPage transactions;
}

Map<String, dynamic> _requiredMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map<Object?, Object?>) {
    return Map<String, dynamic>.from(value);
  }
  throw const FormatException('Expected a JSON object.');
}

List<Object?> _requiredList(Object? value) {
  if (value is List) {
    return value.cast<Object?>();
  }
  throw const FormatException('Expected a JSON list.');
}
