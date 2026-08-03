// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'portfolio_models.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PortfolioAccount _$PortfolioAccountFromJson(Map<String, dynamic> json) =>
    PortfolioAccount(
      id: json['id'] as String,
      name: json['name'] as String,
      platform: json['platform'] as String,
      status: json['status'] as String,
    );

Map<String, dynamic> _$PortfolioAccountToJson(PortfolioAccount instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'platform': instance.platform,
      'status': instance.status,
    };

PortfolioOverview _$PortfolioOverviewFromJson(Map<String, dynamic> json) =>
    PortfolioOverview(
      positionCount: (json['positionCount'] as num).toInt(),
      valuedPositionCount: (json['valuedPositionCount'] as num).toInt(),
      missingValuationCount: (json['missingValuationCount'] as num).toInt(),
      totalHoldingCost: const DecimalJsonConverter().fromJson(
        json['totalHoldingCost'] as Object,
      ),
      valuedHoldingCost: const DecimalJsonConverter().fromJson(
        json['valuedHoldingCost'] as Object,
      ),
      currentMarketValue: const NullableDecimalJsonConverter().fromJson(
        json['currentMarketValue'],
      ),
      currentHoldingProfit: const NullableDecimalJsonConverter().fromJson(
        json['currentHoldingProfit'],
      ),
      currentHoldingReturnPercent: const NullableDecimalJsonConverter()
          .fromJson(json['currentHoldingReturnPercent']),
      realizedProfit: const DecimalJsonConverter().fromJson(
        json['realizedProfit'] as Object,
      ),
      cumulativeProfit: const NullableDecimalJsonConverter().fromJson(
        json['cumulativeProfit'],
      ),
      returnCostBasis: const NullableDecimalJsonConverter().fromJson(
        json['returnCostBasis'],
      ),
      cumulativeReturnPercent: const NullableDecimalJsonConverter().fromJson(
        json['cumulativeReturnPercent'],
      ),
      todayEstimatedProfit: const NullableDecimalJsonConverter().fromJson(
        json['todayEstimatedProfit'],
      ),
      todayEstimateComplete: json['todayEstimateComplete'] as bool,
      confirmedSellCount: (json['confirmedSellCount'] as num).toInt(),
      openSellCount: (json['openSellCount'] as num).toInt(),
      containsEstimatedData: json['containsEstimatedData'] as bool,
      valuationComplete: json['valuationComplete'] as bool,
      valuationStatus: $enumDecodeNullable(
        _$PortfolioValuationStatusEnumMap,
        json['valuationStatus'],
        unknownValue: PortfolioValuationStatus.unavailable,
      ),
      priceType: $enumDecodeNullable(
        _$PortfolioPriceTypeEnumMap,
        json['priceType'],
        unknownValue: PortfolioPriceType.unknown,
      ),
      dataDate: json['dataDate'] == null
          ? null
          : DateTime.parse(json['dataDate'] as String),
      observedAt: json['observedAt'] == null
          ? null
          : DateTime.parse(json['observedAt'] as String),
      holdingStartDate: json['holdingStartDate'] == null
          ? null
          : DateTime.parse(json['holdingStartDate'] as String),
      holdingDays: (json['holdingDays'] as num?)?.toInt(),
    );

Map<String, dynamic> _$PortfolioOverviewToJson(PortfolioOverview instance) =>
    <String, dynamic>{
      'positionCount': instance.positionCount,
      'valuedPositionCount': instance.valuedPositionCount,
      'missingValuationCount': instance.missingValuationCount,
      'totalHoldingCost': const DecimalJsonConverter().toJson(
        instance.totalHoldingCost,
      ),
      'valuedHoldingCost': const DecimalJsonConverter().toJson(
        instance.valuedHoldingCost,
      ),
      'currentMarketValue': const NullableDecimalJsonConverter().toJson(
        instance.currentMarketValue,
      ),
      'currentHoldingProfit': const NullableDecimalJsonConverter().toJson(
        instance.currentHoldingProfit,
      ),
      'currentHoldingReturnPercent': const NullableDecimalJsonConverter()
          .toJson(instance.currentHoldingReturnPercent),
      'realizedProfit': const DecimalJsonConverter().toJson(
        instance.realizedProfit,
      ),
      'cumulativeProfit': const NullableDecimalJsonConverter().toJson(
        instance.cumulativeProfit,
      ),
      'returnCostBasis': const NullableDecimalJsonConverter().toJson(
        instance.returnCostBasis,
      ),
      'cumulativeReturnPercent': const NullableDecimalJsonConverter().toJson(
        instance.cumulativeReturnPercent,
      ),
      'todayEstimatedProfit': const NullableDecimalJsonConverter().toJson(
        instance.todayEstimatedProfit,
      ),
      'todayEstimateComplete': instance.todayEstimateComplete,
      'confirmedSellCount': instance.confirmedSellCount,
      'openSellCount': instance.openSellCount,
      'containsEstimatedData': instance.containsEstimatedData,
      'valuationComplete': instance.valuationComplete,
      'valuationStatus':
          _$PortfolioValuationStatusEnumMap[instance.valuationStatus],
      'priceType': _$PortfolioPriceTypeEnumMap[instance.priceType],
      'dataDate': instance.dataDate?.toIso8601String(),
      'observedAt': instance.observedAt?.toIso8601String(),
      'holdingStartDate': instance.holdingStartDate?.toIso8601String(),
      'holdingDays': instance.holdingDays,
    };

const _$PortfolioValuationStatusEnumMap = {
  PortfolioValuationStatus.live: 'LIVE',
  PortfolioValuationStatus.delayed: 'DELAYED',
  PortfolioValuationStatus.stale: 'STALE',
  PortfolioValuationStatus.official: 'OFFICIAL',
  PortfolioValuationStatus.marketClosed: 'MARKET_CLOSED',
  PortfolioValuationStatus.unavailable: 'UNAVAILABLE',
};

const _$PortfolioPriceTypeEnumMap = {
  PortfolioPriceType.market: 'MARKET',
  PortfolioPriceType.estimated: 'ESTIMATED',
  PortfolioPriceType.official: 'OFFICIAL',
  PortfolioPriceType.mixed: 'MIXED',
  PortfolioPriceType.unknown: 'unknown',
};

FundPortfolioCard _$FundPortfolioCardFromJson(Map<String, dynamic> json) =>
    FundPortfolioCard(
      fundCode: json['fundCode'] as String,
      fundName: json['fundName'] as String,
      primaryTheme: $enumDecode(
        _$FundPrimaryThemeEnumMap,
        json['primaryTheme'],
        unknownValue: FundPrimaryTheme.other,
      ),
      hasCurrentPosition: json['hasCurrentPosition'] as bool,
      accountCount: (json['accountCount'] as num).toInt(),
      totalShares: const NullableDecimalJsonConverter().fromJson(
        json['totalShares'],
      ),
      pendingBuyAmount: const NullableDecimalJsonConverter().fromJson(
        json['pendingBuyAmount'],
      ),
      openTransactionCount: (json['openTransactionCount'] as num).toInt(),
      holdingCost: const NullableDecimalJsonConverter().fromJson(
        json['holdingCost'],
      ),
      currentMarketValue: const NullableDecimalJsonConverter().fromJson(
        json['currentMarketValue'],
      ),
      currentHoldingProfit: const NullableDecimalJsonConverter().fromJson(
        json['currentHoldingProfit'],
      ),
      currentHoldingReturnPercent: const NullableDecimalJsonConverter()
          .fromJson(json['currentHoldingReturnPercent']),
      realizedProfit: const DecimalJsonConverter().fromJson(
        json['realizedProfit'] as Object,
      ),
      cumulativeProfit: const NullableDecimalJsonConverter().fromJson(
        json['cumulativeProfit'],
      ),
      todayEstimatedProfit: const NullableDecimalJsonConverter().fromJson(
        json['todayEstimatedProfit'],
      ),
      todayEstimateComplete: json['todayEstimateComplete'] as bool,
      openSellCount: (json['openSellCount'] as num).toInt(),
      containsEstimatedData: json['containsEstimatedData'] as bool,
      valuationComplete: json['valuationComplete'] as bool,
      valuationStatus: $enumDecodeNullable(
        _$PortfolioValuationStatusEnumMap,
        json['valuationStatus'],
        unknownValue: PortfolioValuationStatus.unavailable,
      ),
      priceType: $enumDecodeNullable(
        _$PortfolioPriceTypeEnumMap,
        json['priceType'],
        unknownValue: PortfolioPriceType.unknown,
      ),
      dataDate: json['dataDate'] == null
          ? null
          : DateTime.parse(json['dataDate'] as String),
      observedAt: json['observedAt'] == null
          ? null
          : DateTime.parse(json['observedAt'] as String),
      holdingStartDate: json['holdingStartDate'] == null
          ? null
          : DateTime.parse(json['holdingStartDate'] as String),
      holdingDays: (json['holdingDays'] as num?)?.toInt(),
    );

Map<String, dynamic> _$FundPortfolioCardToJson(FundPortfolioCard instance) =>
    <String, dynamic>{
      'fundCode': instance.fundCode,
      'fundName': instance.fundName,
      'primaryTheme': _$FundPrimaryThemeEnumMap[instance.primaryTheme]!,
      'hasCurrentPosition': instance.hasCurrentPosition,
      'accountCount': instance.accountCount,
      'totalShares': const NullableDecimalJsonConverter().toJson(
        instance.totalShares,
      ),
      'pendingBuyAmount': const NullableDecimalJsonConverter().toJson(
        instance.pendingBuyAmount,
      ),
      'openTransactionCount': instance.openTransactionCount,
      'holdingCost': const NullableDecimalJsonConverter().toJson(
        instance.holdingCost,
      ),
      'currentMarketValue': const NullableDecimalJsonConverter().toJson(
        instance.currentMarketValue,
      ),
      'currentHoldingProfit': const NullableDecimalJsonConverter().toJson(
        instance.currentHoldingProfit,
      ),
      'currentHoldingReturnPercent': const NullableDecimalJsonConverter()
          .toJson(instance.currentHoldingReturnPercent),
      'realizedProfit': const DecimalJsonConverter().toJson(
        instance.realizedProfit,
      ),
      'cumulativeProfit': const NullableDecimalJsonConverter().toJson(
        instance.cumulativeProfit,
      ),
      'todayEstimatedProfit': const NullableDecimalJsonConverter().toJson(
        instance.todayEstimatedProfit,
      ),
      'todayEstimateComplete': instance.todayEstimateComplete,
      'openSellCount': instance.openSellCount,
      'containsEstimatedData': instance.containsEstimatedData,
      'valuationComplete': instance.valuationComplete,
      'valuationStatus':
          _$PortfolioValuationStatusEnumMap[instance.valuationStatus],
      'priceType': _$PortfolioPriceTypeEnumMap[instance.priceType],
      'dataDate': instance.dataDate?.toIso8601String(),
      'observedAt': instance.observedAt?.toIso8601String(),
      'holdingStartDate': instance.holdingStartDate?.toIso8601String(),
      'holdingDays': instance.holdingDays,
    };

const _$FundPrimaryThemeEnumMap = {
  FundPrimaryTheme.semiconductor: 'SEMICONDUCTOR',
  FundPrimaryTheme.internet: 'INTERNET',
  FundPrimaryTheme.consumer: 'CONSUMER',
  FundPrimaryTheme.healthcare: 'HEALTHCARE',
  FundPrimaryTheme.newEnergy: 'NEW_ENERGY',
  FundPrimaryTheme.broadIndex: 'BROAD_INDEX',
  FundPrimaryTheme.finance: 'FINANCE',
  FundPrimaryTheme.overseas: 'OVERSEAS',
  FundPrimaryTheme.mixed: 'MIXED',
  FundPrimaryTheme.other: 'OTHER',
};

FundPortfolioAccount _$FundPortfolioAccountFromJson(
  Map<String, dynamic> json,
) => FundPortfolioAccount(
  positionId: json['positionId'] as String,
  accountId: json['accountId'] as String,
  accountName: json['accountName'] as String,
  accountPlatform: json['accountPlatform'] as String,
  shares: const NullableDecimalJsonConverter().fromJson(json['shares']),
  holdingCost: const NullableDecimalJsonConverter().fromJson(
    json['holdingCost'],
  ),
  currentMarketValue: const NullableDecimalJsonConverter().fromJson(
    json['currentMarketValue'],
  ),
  currentHoldingProfit: const NullableDecimalJsonConverter().fromJson(
    json['currentHoldingProfit'],
  ),
  currentHoldingReturnPercent: const NullableDecimalJsonConverter().fromJson(
    json['currentHoldingReturnPercent'],
  ),
  realizedProfit: const DecimalJsonConverter().fromJson(
    json['realizedProfit'] as Object,
  ),
  cumulativeProfit: const NullableDecimalJsonConverter().fromJson(
    json['cumulativeProfit'],
  ),
  todayEstimatedProfit: const NullableDecimalJsonConverter().fromJson(
    json['todayEstimatedProfit'],
  ),
  openSellCount: (json['openSellCount'] as num).toInt(),
  positionStatus: json['positionStatus'] as String,
  valuationStatus: $enumDecodeNullable(
    _$PortfolioValuationStatusEnumMap,
    json['valuationStatus'],
    unknownValue: PortfolioValuationStatus.unavailable,
  ),
  priceType: $enumDecodeNullable(
    _$PortfolioPriceTypeEnumMap,
    json['priceType'],
    unknownValue: PortfolioPriceType.unknown,
  ),
  unitNav: const NullableDecimalJsonConverter().fromJson(json['unitNav']),
  estimatedChangePercent: const NullableDecimalJsonConverter().fromJson(
    json['estimatedChangePercent'],
  ),
  baseNavDate: json['baseNavDate'] == null
      ? null
      : DateTime.parse(json['baseNavDate'] as String),
  baseNav: const NullableDecimalJsonConverter().fromJson(json['baseNav']),
  dataDate: json['dataDate'] == null
      ? null
      : DateTime.parse(json['dataDate'] as String),
  observedAt: json['observedAt'] == null
      ? null
      : DateTime.parse(json['observedAt'] as String),
  dataSource: json['dataSource'] as String?,
  holdingStartDate: json['holdingStartDate'] == null
      ? null
      : DateTime.parse(json['holdingStartDate'] as String),
  holdingDays: (json['holdingDays'] as num?)?.toInt(),
);

Map<String, dynamic> _$FundPortfolioAccountToJson(
  FundPortfolioAccount instance,
) => <String, dynamic>{
  'positionId': instance.positionId,
  'accountId': instance.accountId,
  'accountName': instance.accountName,
  'accountPlatform': instance.accountPlatform,
  'shares': const NullableDecimalJsonConverter().toJson(instance.shares),
  'holdingCost': const NullableDecimalJsonConverter().toJson(
    instance.holdingCost,
  ),
  'currentMarketValue': const NullableDecimalJsonConverter().toJson(
    instance.currentMarketValue,
  ),
  'currentHoldingProfit': const NullableDecimalJsonConverter().toJson(
    instance.currentHoldingProfit,
  ),
  'currentHoldingReturnPercent': const NullableDecimalJsonConverter().toJson(
    instance.currentHoldingReturnPercent,
  ),
  'realizedProfit': const DecimalJsonConverter().toJson(
    instance.realizedProfit,
  ),
  'cumulativeProfit': const NullableDecimalJsonConverter().toJson(
    instance.cumulativeProfit,
  ),
  'todayEstimatedProfit': const NullableDecimalJsonConverter().toJson(
    instance.todayEstimatedProfit,
  ),
  'openSellCount': instance.openSellCount,
  'positionStatus': instance.positionStatus,
  'valuationStatus':
      _$PortfolioValuationStatusEnumMap[instance.valuationStatus],
  'priceType': _$PortfolioPriceTypeEnumMap[instance.priceType],
  'unitNav': const NullableDecimalJsonConverter().toJson(instance.unitNav),
  'estimatedChangePercent': const NullableDecimalJsonConverter().toJson(
    instance.estimatedChangePercent,
  ),
  'baseNavDate': instance.baseNavDate?.toIso8601String(),
  'baseNav': const NullableDecimalJsonConverter().toJson(instance.baseNav),
  'dataDate': instance.dataDate?.toIso8601String(),
  'observedAt': instance.observedAt?.toIso8601String(),
  'dataSource': instance.dataSource,
  'holdingStartDate': instance.holdingStartDate?.toIso8601String(),
  'holdingDays': instance.holdingDays,
};
