import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('zh')];

  /// No description provided for @appTitle.
  ///
  /// In zh, this message translates to:
  /// **'Fund Keeper'**
  String get appTitle;

  /// No description provided for @homeTab.
  ///
  /// In zh, this message translates to:
  /// **'首页'**
  String get homeTab;

  /// No description provided for @recordsTab.
  ///
  /// In zh, this message translates to:
  /// **'记录'**
  String get recordsTab;

  /// No description provided for @profileTab.
  ///
  /// In zh, this message translates to:
  /// **'我的'**
  String get profileTab;

  /// No description provided for @createAction.
  ///
  /// In zh, this message translates to:
  /// **'新增'**
  String get createAction;

  /// No description provided for @chooseEntryMethod.
  ///
  /// In zh, this message translates to:
  /// **'选择录入方式'**
  String get chooseEntryMethod;

  /// No description provided for @manualBuyTitle.
  ///
  /// In zh, this message translates to:
  /// **'手动录入买入'**
  String get manualBuyTitle;

  /// No description provided for @manualBuyDescription.
  ///
  /// In zh, this message translates to:
  /// **'只需填写账户、基金代码、购买金额和提交时间'**
  String get manualBuyDescription;

  /// No description provided for @manualSellTitle.
  ///
  /// In zh, this message translates to:
  /// **'手动录入卖出'**
  String get manualSellTitle;

  /// No description provided for @manualSellDescription.
  ///
  /// In zh, this message translates to:
  /// **'从当前持仓选择基金，记录部分卖出或全部卖出'**
  String get manualSellDescription;

  /// No description provided for @sellMode.
  ///
  /// In zh, this message translates to:
  /// **'卖出方式'**
  String get sellMode;

  /// No description provided for @partialSell.
  ///
  /// In zh, this message translates to:
  /// **'部分卖出'**
  String get partialSell;

  /// No description provided for @fullSell.
  ///
  /// In zh, this message translates to:
  /// **'全部卖出'**
  String get fullSell;

  /// No description provided for @holdingFund.
  ///
  /// In zh, this message translates to:
  /// **'持仓基金'**
  String get holdingFund;

  /// No description provided for @selectHoldingFund.
  ///
  /// In zh, this message translates to:
  /// **'请选择要卖出的持仓基金'**
  String get selectHoldingFund;

  /// No description provided for @noSellablePosition.
  ///
  /// In zh, this message translates to:
  /// **'该账户暂无可卖出的基金持仓'**
  String get noSellablePosition;

  /// No description provided for @positionsLoadFailed.
  ///
  /// In zh, this message translates to:
  /// **'持仓基金加载失败'**
  String get positionsLoadFailed;

  /// No description provided for @positionShares.
  ///
  /// In zh, this message translates to:
  /// **'当前份额'**
  String get positionShares;

  /// No description provided for @positionMarketValue.
  ///
  /// In zh, this message translates to:
  /// **'当前市值'**
  String get positionMarketValue;

  /// No description provided for @expectedReceivedAmount.
  ///
  /// In zh, this message translates to:
  /// **'预计到账金额'**
  String get expectedReceivedAmount;

  /// No description provided for @expectedReceivedAmountHint.
  ///
  /// In zh, this message translates to:
  /// **'例如 2000.00'**
  String get expectedReceivedAmountHint;

  /// No description provided for @partialSellHint.
  ///
  /// In zh, this message translates to:
  /// **'只需填写预计到账金额，平台确认份额和实际到账金额可稍后补充。'**
  String get partialSellHint;

  /// No description provided for @fullSellHint.
  ///
  /// In zh, this message translates to:
  /// **'先创建待确认的全部卖出记录，平台最终到账后再补充结果。'**
  String get fullSellHint;

  /// No description provided for @sellNote.
  ///
  /// In zh, this message translates to:
  /// **'备注（可选）'**
  String get sellNote;

  /// No description provided for @sellNoteHint.
  ///
  /// In zh, this message translates to:
  /// **'例如：支付宝部分卖出'**
  String get sellNoteHint;

  /// No description provided for @openSellBlocked.
  ///
  /// In zh, this message translates to:
  /// **'该基金已有待处理卖出，请先确认或标记原交易未完成。'**
  String get openSellBlocked;

  /// No description provided for @submitSell.
  ///
  /// In zh, this message translates to:
  /// **'确认录入卖出'**
  String get submitSell;

  /// No description provided for @manualSellSuccess.
  ///
  /// In zh, this message translates to:
  /// **'卖出记录已创建'**
  String get manualSellSuccess;

  /// No description provided for @estimatedSoldShares.
  ///
  /// In zh, this message translates to:
  /// **'预计减少份额'**
  String get estimatedSoldShares;

  /// No description provided for @sellResultHint.
  ///
  /// In zh, this message translates to:
  /// **'平台最终到账金额及确认份额可在交易记录中补充。'**
  String get sellResultHint;

  /// No description provided for @jsonImportTitle.
  ///
  /// In zh, this message translates to:
  /// **'JSON 批量导入'**
  String get jsonImportTitle;

  /// No description provided for @jsonImportDescription.
  ///
  /// In zh, this message translates to:
  /// **'先预检逐行结果，确认后才写入持仓'**
  String get jsonImportDescription;

  /// No description provided for @platformAccount.
  ///
  /// In zh, this message translates to:
  /// **'平台账户'**
  String get platformAccount;

  /// No description provided for @selectAccount.
  ///
  /// In zh, this message translates to:
  /// **'请选择平台账户'**
  String get selectAccount;

  /// No description provided for @noActiveAccount.
  ///
  /// In zh, this message translates to:
  /// **'暂无可用账户，请先在“我的”中创建账户'**
  String get noActiveAccount;

  /// No description provided for @fundCode.
  ///
  /// In zh, this message translates to:
  /// **'基金代码'**
  String get fundCode;

  /// No description provided for @fundCodeHint.
  ///
  /// In zh, this message translates to:
  /// **'6 位数字，例如 005827'**
  String get fundCodeHint;

  /// No description provided for @fundCodeInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入6位基金代码'**
  String get fundCodeInvalid;

  /// No description provided for @purchaseAmount.
  ///
  /// In zh, this message translates to:
  /// **'购买金额'**
  String get purchaseAmount;

  /// No description provided for @amountHint.
  ///
  /// In zh, this message translates to:
  /// **'例如 5000.00'**
  String get amountHint;

  /// No description provided for @amountInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入大于0、最多4位小数的金额'**
  String get amountInvalid;

  /// No description provided for @submittedDate.
  ///
  /// In zh, this message translates to:
  /// **'提交日期'**
  String get submittedDate;

  /// No description provided for @submittedPeriod.
  ///
  /// In zh, this message translates to:
  /// **'提交时段'**
  String get submittedPeriod;

  /// No description provided for @before15.
  ///
  /// In zh, this message translates to:
  /// **'15:00 前'**
  String get before15;

  /// No description provided for @after15.
  ///
  /// In zh, this message translates to:
  /// **'15:00 后'**
  String get after15;

  /// No description provided for @submitBuy.
  ///
  /// In zh, this message translates to:
  /// **'确认录入'**
  String get submitBuy;

  /// No description provided for @submitting.
  ///
  /// In zh, this message translates to:
  /// **'正在提交'**
  String get submitting;

  /// No description provided for @manualBuySuccess.
  ///
  /// In zh, this message translates to:
  /// **'买入记录已创建'**
  String get manualBuySuccess;

  /// No description provided for @transactionStatus.
  ///
  /// In zh, this message translates to:
  /// **'交易状态'**
  String get transactionStatus;

  /// No description provided for @statusEstimated.
  ///
  /// In zh, this message translates to:
  /// **'估算持仓'**
  String get statusEstimated;

  /// No description provided for @statusPending.
  ///
  /// In zh, this message translates to:
  /// **'等待净值或确认'**
  String get statusPending;

  /// No description provided for @statusConfirmed.
  ///
  /// In zh, this message translates to:
  /// **'平台已确认'**
  String get statusConfirmed;

  /// No description provided for @effectiveTradeDate.
  ///
  /// In zh, this message translates to:
  /// **'有效交易日'**
  String get effectiveTradeDate;

  /// No description provided for @estimatedShares.
  ///
  /// In zh, this message translates to:
  /// **'估算份额'**
  String get estimatedShares;

  /// No description provided for @complete.
  ///
  /// In zh, this message translates to:
  /// **'完成'**
  String get complete;

  /// No description provided for @entryFailed.
  ///
  /// In zh, this message translates to:
  /// **'录入失败，请稍后重试'**
  String get entryFailed;

  /// No description provided for @importKind.
  ///
  /// In zh, this message translates to:
  /// **'导入类型'**
  String get importKind;

  /// No description provided for @positionSnapshot.
  ///
  /// In zh, this message translates to:
  /// **'持仓快照'**
  String get positionSnapshot;

  /// No description provided for @transactionBatch.
  ///
  /// In zh, this message translates to:
  /// **'交易流水'**
  String get transactionBatch;

  /// No description provided for @positionSnapshotHint.
  ///
  /// In zh, this message translates to:
  /// **'用于录入当前持仓；PARTIAL 不影响未列出的基金，FULL_ACCOUNT 可能清空未列出的持仓。'**
  String get positionSnapshotHint;

  /// No description provided for @transactionBatchHint.
  ///
  /// In zh, this message translates to:
  /// **'用于按顺序导入买入或卖出流水，受已有快照时间边界约束。'**
  String get transactionBatchHint;

  /// No description provided for @jsonContent.
  ///
  /// In zh, this message translates to:
  /// **'JSON 内容'**
  String get jsonContent;

  /// No description provided for @jsonContentHint.
  ///
  /// In zh, this message translates to:
  /// **'粘贴完整 JSON，importType 必须与上方选择一致'**
  String get jsonContentHint;

  /// No description provided for @jsonRequired.
  ///
  /// In zh, this message translates to:
  /// **'请粘贴 JSON 内容'**
  String get jsonRequired;

  /// No description provided for @preflight.
  ///
  /// In zh, this message translates to:
  /// **'开始预检'**
  String get preflight;

  /// No description provided for @preflighting.
  ///
  /// In zh, this message translates to:
  /// **'正在预检'**
  String get preflighting;

  /// No description provided for @preflightResult.
  ///
  /// In zh, this message translates to:
  /// **'预检结果'**
  String get preflightResult;

  /// No description provided for @readyToCommit.
  ///
  /// In zh, this message translates to:
  /// **'预检通过，可以确认导入'**
  String get readyToCommit;

  /// No description provided for @preflightFailed.
  ///
  /// In zh, this message translates to:
  /// **'预检未通过，请修复 JSON 后重试'**
  String get preflightFailed;

  /// No description provided for @alreadyCommitted.
  ///
  /// In zh, this message translates to:
  /// **'该批次已经成功导入'**
  String get alreadyCommitted;

  /// No description provided for @batchId.
  ///
  /// In zh, this message translates to:
  /// **'批次 ID'**
  String get batchId;

  /// No description provided for @totalRows.
  ///
  /// In zh, this message translates to:
  /// **'总行数'**
  String get totalRows;

  /// No description provided for @importableRows.
  ///
  /// In zh, this message translates to:
  /// **'可导入'**
  String get importableRows;

  /// No description provided for @warnings.
  ///
  /// In zh, this message translates to:
  /// **'警告'**
  String get warnings;

  /// No description provided for @errors.
  ///
  /// In zh, this message translates to:
  /// **'错误'**
  String get errors;

  /// No description provided for @calibrations.
  ///
  /// In zh, this message translates to:
  /// **'需校准'**
  String get calibrations;

  /// No description provided for @willCreateAccount.
  ///
  /// In zh, this message translates to:
  /// **'确认时将创建账户'**
  String get willCreateAccount;

  /// No description provided for @rowNumber.
  ///
  /// In zh, this message translates to:
  /// **'第 {row} 行'**
  String rowNumber(int row);

  /// No description provided for @confirmImport.
  ///
  /// In zh, this message translates to:
  /// **'确认整批导入'**
  String get confirmImport;

  /// No description provided for @retryCommit.
  ///
  /// In zh, this message translates to:
  /// **'重试确认'**
  String get retryCommit;

  /// No description provided for @committing.
  ///
  /// In zh, this message translates to:
  /// **'正在确认'**
  String get committing;

  /// No description provided for @importSuccess.
  ///
  /// In zh, this message translates to:
  /// **'批量导入成功'**
  String get importSuccess;

  /// No description provided for @appliedRows.
  ///
  /// In zh, this message translates to:
  /// **'已处理行数'**
  String get appliedRows;

  /// No description provided for @accountCreated.
  ///
  /// In zh, this message translates to:
  /// **'平台账户已创建'**
  String get accountCreated;

  /// No description provided for @repreflightRequired.
  ///
  /// In zh, this message translates to:
  /// **'服务端状态可能已变化，请重新预检'**
  String get repreflightRequired;

  /// No description provided for @importFailed.
  ///
  /// In zh, this message translates to:
  /// **'导入失败，请稍后重试'**
  String get importFailed;

  /// No description provided for @homeFoundationTitle.
  ///
  /// In zh, this message translates to:
  /// **'还没有基金持仓'**
  String get homeFoundationTitle;

  /// No description provided for @homeFoundationDescription.
  ///
  /// In zh, this message translates to:
  /// **'可以逐只录入基金，也可以通过 JSON 批量导入'**
  String get homeFoundationDescription;

  /// No description provided for @allAccounts.
  ///
  /// In zh, this message translates to:
  /// **'全部账户'**
  String get allAccounts;

  /// No description provided for @totalAssets.
  ///
  /// In zh, this message translates to:
  /// **'总资产'**
  String get totalAssets;

  /// No description provided for @holdingCost.
  ///
  /// In zh, this message translates to:
  /// **'持仓成本'**
  String get holdingCost;

  /// No description provided for @cumulativeProfit.
  ///
  /// In zh, this message translates to:
  /// **'累计收益'**
  String get cumulativeProfit;

  /// No description provided for @todayEstimatedProfit.
  ///
  /// In zh, this message translates to:
  /// **'今日收益'**
  String get todayEstimatedProfit;

  /// No description provided for @holdingFunds.
  ///
  /// In zh, this message translates to:
  /// **'持仓基金'**
  String get holdingFunds;

  /// No description provided for @valuationStatus.
  ///
  /// In zh, this message translates to:
  /// **'行情与净值'**
  String get valuationStatus;

  /// No description provided for @refreshValuation.
  ///
  /// In zh, this message translates to:
  /// **'刷新行情'**
  String get refreshValuation;

  /// No description provided for @lastUpdated.
  ///
  /// In zh, this message translates to:
  /// **'更新时间'**
  String get lastUpdated;

  /// No description provided for @valuationLive.
  ///
  /// In zh, this message translates to:
  /// **'场内 ETF 实时行情'**
  String get valuationLive;

  /// No description provided for @valuationDelayed.
  ///
  /// In zh, this message translates to:
  /// **'场内 ETF 行情延迟'**
  String get valuationDelayed;

  /// No description provided for @valuationStale.
  ///
  /// In zh, this message translates to:
  /// **'场内 ETF 行情已过期'**
  String get valuationStale;

  /// No description provided for @valuationOfficial.
  ///
  /// In zh, this message translates to:
  /// **'场外基金正式净值'**
  String get valuationOfficial;

  /// No description provided for @valuationMixed.
  ///
  /// In zh, this message translates to:
  /// **'ETF 行情 + 场外正式净值'**
  String get valuationMixed;

  /// No description provided for @valuationMarketClosed.
  ///
  /// In zh, this message translates to:
  /// **'场内 ETF 已收盘'**
  String get valuationMarketClosed;

  /// No description provided for @valuationUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'行情或净值不可用'**
  String get valuationUnavailable;

  /// No description provided for @valuationPartial.
  ///
  /// In zh, this message translates to:
  /// **'部分基金暂无行情或正式净值，汇总数据不完整'**
  String get valuationPartial;

  /// No description provided for @todayEstimatePartial.
  ///
  /// In zh, this message translates to:
  /// **'部分基金暂无今日收益数据'**
  String get todayEstimatePartial;

  /// No description provided for @containsEstimatedData.
  ///
  /// In zh, this message translates to:
  /// **'包含尚未确认的估算持仓'**
  String get containsEstimatedData;

  /// No description provided for @themeDistribution.
  ///
  /// In zh, this message translates to:
  /// **'板块分布'**
  String get themeDistribution;

  /// No description provided for @themeDistributionHint.
  ///
  /// In zh, this message translates to:
  /// **'按当前市值计算，点击板块筛选基金'**
  String get themeDistributionHint;

  /// No description provided for @themeNoData.
  ///
  /// In zh, this message translates to:
  /// **'暂无可用于板块分布的市值数据'**
  String get themeNoData;

  /// No description provided for @themeAll.
  ///
  /// In zh, this message translates to:
  /// **'全部'**
  String get themeAll;

  /// No description provided for @themeSemiconductor.
  ///
  /// In zh, this message translates to:
  /// **'半导体'**
  String get themeSemiconductor;

  /// No description provided for @themeInternet.
  ///
  /// In zh, this message translates to:
  /// **'互联网'**
  String get themeInternet;

  /// No description provided for @themeConsumer.
  ///
  /// In zh, this message translates to:
  /// **'消费'**
  String get themeConsumer;

  /// No description provided for @themeHealthcare.
  ///
  /// In zh, this message translates to:
  /// **'医疗健康'**
  String get themeHealthcare;

  /// No description provided for @themeNewEnergy.
  ///
  /// In zh, this message translates to:
  /// **'新能源'**
  String get themeNewEnergy;

  /// No description provided for @themeBroadIndex.
  ///
  /// In zh, this message translates to:
  /// **'宽基指数'**
  String get themeBroadIndex;

  /// No description provided for @themeFinance.
  ///
  /// In zh, this message translates to:
  /// **'金融'**
  String get themeFinance;

  /// No description provided for @themeOverseas.
  ///
  /// In zh, this message translates to:
  /// **'海外'**
  String get themeOverseas;

  /// No description provided for @themeMixed.
  ///
  /// In zh, this message translates to:
  /// **'混合'**
  String get themeMixed;

  /// No description provided for @themeOther.
  ///
  /// In zh, this message translates to:
  /// **'其他'**
  String get themeOther;

  /// No description provided for @myFunds.
  ///
  /// In zh, this message translates to:
  /// **'我的基金'**
  String get myFunds;

  /// No description provided for @holdingAmount.
  ///
  /// In zh, this message translates to:
  /// **'持仓金额'**
  String get holdingAmount;

  /// No description provided for @holdingProfit.
  ///
  /// In zh, this message translates to:
  /// **'持仓收益'**
  String get holdingProfit;

  /// No description provided for @todayProfit.
  ///
  /// In zh, this message translates to:
  /// **'今日收益'**
  String get todayProfit;

  /// No description provided for @holdingDays.
  ///
  /// In zh, this message translates to:
  /// **'持有天数'**
  String get holdingDays;

  /// No description provided for @pendingBuy.
  ///
  /// In zh, this message translates to:
  /// **'待确认买入'**
  String get pendingBuy;

  /// No description provided for @pendingConfirmation.
  ///
  /// In zh, this message translates to:
  /// **'存在待确认交易'**
  String get pendingConfirmation;

  /// No description provided for @manualEntry.
  ///
  /// In zh, this message translates to:
  /// **'手动录入'**
  String get manualEntry;

  /// No description provided for @jsonImport.
  ///
  /// In zh, this message translates to:
  /// **'JSON 批量导入'**
  String get jsonImport;

  /// No description provided for @portfolioLoadFailed.
  ///
  /// In zh, this message translates to:
  /// **'持仓数据加载失败'**
  String get portfolioLoadFailed;

  /// No description provided for @accountsLoadFailed.
  ///
  /// In zh, this message translates to:
  /// **'账户加载失败'**
  String get accountsLoadFailed;

  /// No description provided for @recordsFoundationTitle.
  ///
  /// In zh, this message translates to:
  /// **'交易记录将在后续业务切片接入'**
  String get recordsFoundationTitle;

  /// No description provided for @transactionRecords.
  ///
  /// In zh, this message translates to:
  /// **'交易记录'**
  String get transactionRecords;

  /// No description provided for @transactionRecordCount.
  ///
  /// In zh, this message translates to:
  /// **'共 {count} 条'**
  String transactionRecordCount(int count);

  /// No description provided for @allTypes.
  ///
  /// In zh, this message translates to:
  /// **'全部类型'**
  String get allTypes;

  /// No description provided for @transactionType.
  ///
  /// In zh, this message translates to:
  /// **'交易类型'**
  String get transactionType;

  /// No description provided for @allStatuses.
  ///
  /// In zh, this message translates to:
  /// **'全部状态'**
  String get allStatuses;

  /// No description provided for @transactionBuy.
  ///
  /// In zh, this message translates to:
  /// **'买入'**
  String get transactionBuy;

  /// No description provided for @transactionSell.
  ///
  /// In zh, this message translates to:
  /// **'卖出'**
  String get transactionSell;

  /// No description provided for @transactionAdjustment.
  ///
  /// In zh, this message translates to:
  /// **'持仓调整'**
  String get transactionAdjustment;

  /// No description provided for @statusNeedsCalibration.
  ///
  /// In zh, this message translates to:
  /// **'需要校准'**
  String get statusNeedsCalibration;

  /// No description provided for @statusCancelled.
  ///
  /// In zh, this message translates to:
  /// **'已撤销'**
  String get statusCancelled;

  /// No description provided for @statusReversed.
  ///
  /// In zh, this message translates to:
  /// **'已冲正'**
  String get statusReversed;

  /// No description provided for @fundCodeFilter.
  ///
  /// In zh, this message translates to:
  /// **'按基金代码筛选'**
  String get fundCodeFilter;

  /// No description provided for @filter.
  ///
  /// In zh, this message translates to:
  /// **'筛选'**
  String get filter;

  /// No description provided for @clearFilters.
  ///
  /// In zh, this message translates to:
  /// **'清除筛选'**
  String get clearFilters;

  /// No description provided for @transactionListEmpty.
  ///
  /// In zh, this message translates to:
  /// **'没有符合条件的交易记录'**
  String get transactionListEmpty;

  /// No description provided for @transactionLoadFailed.
  ///
  /// In zh, this message translates to:
  /// **'交易记录加载失败'**
  String get transactionLoadFailed;

  /// No description provided for @loadMore.
  ///
  /// In zh, this message translates to:
  /// **'加载更多'**
  String get loadMore;

  /// No description provided for @loadingMore.
  ///
  /// In zh, this message translates to:
  /// **'正在加载'**
  String get loadingMore;

  /// No description provided for @noMoreRecords.
  ///
  /// In zh, this message translates to:
  /// **'没有更多记录了'**
  String get noMoreRecords;

  /// No description provided for @submittedTime.
  ///
  /// In zh, this message translates to:
  /// **'提交时间'**
  String get submittedTime;

  /// No description provided for @receivedAmount.
  ///
  /// In zh, this message translates to:
  /// **'实际到账'**
  String get receivedAmount;

  /// No description provided for @expectedAmount.
  ///
  /// In zh, this message translates to:
  /// **'预计金额'**
  String get expectedAmount;

  /// No description provided for @realizedProfit.
  ///
  /// In zh, this message translates to:
  /// **'已实现收益'**
  String get realizedProfit;

  /// No description provided for @confirmTransaction.
  ///
  /// In zh, this message translates to:
  /// **'补充平台结果'**
  String get confirmTransaction;

  /// No description provided for @confirmedShares.
  ///
  /// In zh, this message translates to:
  /// **'平台确认份额'**
  String get confirmedShares;

  /// No description provided for @confirmedSharesHint.
  ///
  /// In zh, this message translates to:
  /// **'最多8位小数'**
  String get confirmedSharesHint;

  /// No description provided for @confirmedSharesInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入大于0、最多8位小数的份额'**
  String get confirmedSharesInvalid;

  /// No description provided for @actualReceivedAmount.
  ///
  /// In zh, this message translates to:
  /// **'实际到账金额'**
  String get actualReceivedAmount;

  /// No description provided for @actualReceivedAmountInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入大于0、最多4位小数的金额'**
  String get actualReceivedAmountInvalid;

  /// No description provided for @confirmedDate.
  ///
  /// In zh, this message translates to:
  /// **'确认日期'**
  String get confirmedDate;

  /// No description provided for @confirm.
  ///
  /// In zh, this message translates to:
  /// **'确认'**
  String get confirm;

  /// No description provided for @cancelTransaction.
  ///
  /// In zh, this message translates to:
  /// **'标记交易未完成'**
  String get cancelTransaction;

  /// No description provided for @cancelTransactionDescription.
  ///
  /// In zh, this message translates to:
  /// **'撤销后会恢复该笔预估交易对持仓的影响，交易记录仍会保留。'**
  String get cancelTransactionDescription;

  /// No description provided for @cancelReason.
  ///
  /// In zh, this message translates to:
  /// **'原因（可选）'**
  String get cancelReason;

  /// No description provided for @cancelReasonHint.
  ///
  /// In zh, this message translates to:
  /// **'例如：平台最终未成交'**
  String get cancelReasonHint;

  /// No description provided for @transactionConfirmed.
  ///
  /// In zh, this message translates to:
  /// **'交易已确认'**
  String get transactionConfirmed;

  /// No description provided for @transactionCancelled.
  ///
  /// In zh, this message translates to:
  /// **'交易已标记为未完成'**
  String get transactionCancelled;

  /// No description provided for @transactionActionFailed.
  ///
  /// In zh, this message translates to:
  /// **'操作失败，请稍后重试'**
  String get transactionActionFailed;

  /// No description provided for @pendingOfficialNav.
  ///
  /// In zh, this message translates to:
  /// **'等待正式净值'**
  String get pendingOfficialNav;

  /// No description provided for @pendingFeeRule.
  ///
  /// In zh, this message translates to:
  /// **'等待申购费率'**
  String get pendingFeeRule;

  /// No description provided for @pendingNavAndFee.
  ///
  /// In zh, this message translates to:
  /// **'等待净值和费率'**
  String get pendingNavAndFee;

  /// No description provided for @pendingSellConfirmation.
  ///
  /// In zh, this message translates to:
  /// **'等待卖出确认'**
  String get pendingSellConfirmation;

  /// No description provided for @platformAccounts.
  ///
  /// In zh, this message translates to:
  /// **'平台账户'**
  String get platformAccounts;

  /// No description provided for @platformAccountsDescription.
  ///
  /// In zh, this message translates to:
  /// **'不同平台的同名基金会分别记录持仓和收益'**
  String get platformAccountsDescription;

  /// No description provided for @activeAccountCount.
  ///
  /// In zh, this message translates to:
  /// **'{count} 个有效账户'**
  String activeAccountCount(int count);

  /// No description provided for @addPlatformAccount.
  ///
  /// In zh, this message translates to:
  /// **'新增平台账户'**
  String get addPlatformAccount;

  /// No description provided for @editPlatformAccount.
  ///
  /// In zh, this message translates to:
  /// **'修改平台账户'**
  String get editPlatformAccount;

  /// No description provided for @accountName.
  ///
  /// In zh, this message translates to:
  /// **'账户名称'**
  String get accountName;

  /// No description provided for @accountNameHint.
  ///
  /// In zh, this message translates to:
  /// **'例如：我的支付宝'**
  String get accountNameHint;

  /// No description provided for @accountNameInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入1～100个字符的账户名称'**
  String get accountNameInvalid;

  /// No description provided for @accountPlatform.
  ///
  /// In zh, this message translates to:
  /// **'平台类型'**
  String get accountPlatform;

  /// No description provided for @platformAlipay.
  ///
  /// In zh, this message translates to:
  /// **'支付宝'**
  String get platformAlipay;

  /// No description provided for @platformTiantianFund.
  ///
  /// In zh, this message translates to:
  /// **'天天基金'**
  String get platformTiantianFund;

  /// No description provided for @platformBank.
  ///
  /// In zh, this message translates to:
  /// **'银行'**
  String get platformBank;

  /// No description provided for @platformOther.
  ///
  /// In zh, this message translates to:
  /// **'其他'**
  String get platformOther;

  /// No description provided for @accountActive.
  ///
  /// In zh, this message translates to:
  /// **'有效'**
  String get accountActive;

  /// No description provided for @accountArchived.
  ///
  /// In zh, this message translates to:
  /// **'已归档'**
  String get accountArchived;

  /// No description provided for @accountArchivedHint.
  ///
  /// In zh, this message translates to:
  /// **'归档账户仅保留历史记录，不能继续录入交易'**
  String get accountArchivedHint;

  /// No description provided for @editAccount.
  ///
  /// In zh, this message translates to:
  /// **'编辑账户'**
  String get editAccount;

  /// No description provided for @archiveAccount.
  ///
  /// In zh, this message translates to:
  /// **'归档账户'**
  String get archiveAccount;

  /// No description provided for @archiveAccountDescription.
  ///
  /// In zh, this message translates to:
  /// **'只有没有当前持仓和待确认交易的账户可以归档。归档后仍保留历史记录。'**
  String get archiveAccountDescription;

  /// No description provided for @lastActiveAccountHint.
  ///
  /// In zh, this message translates to:
  /// **'至少需要保留一个有效账户'**
  String get lastActiveAccountHint;

  /// No description provided for @save.
  ///
  /// In zh, this message translates to:
  /// **'保存'**
  String get save;

  /// No description provided for @accountUpdated.
  ///
  /// In zh, this message translates to:
  /// **'平台账户已更新'**
  String get accountUpdated;

  /// No description provided for @accountArchivedSuccess.
  ///
  /// In zh, this message translates to:
  /// **'平台账户已归档'**
  String get accountArchivedSuccess;

  /// No description provided for @accountActionFailed.
  ///
  /// In zh, this message translates to:
  /// **'账户操作失败，请稍后重试'**
  String get accountActionFailed;

  /// No description provided for @accountListLoadFailed.
  ///
  /// In zh, this message translates to:
  /// **'平台账户加载失败'**
  String get accountListLoadFailed;

  /// No description provided for @welcomeBack.
  ///
  /// In zh, this message translates to:
  /// **'欢迎回来'**
  String get welcomeBack;

  /// No description provided for @loginDescription.
  ///
  /// In zh, this message translates to:
  /// **'登录后查看和管理你的基金持仓'**
  String get loginDescription;

  /// No description provided for @email.
  ///
  /// In zh, this message translates to:
  /// **'邮箱'**
  String get email;

  /// No description provided for @emailRequired.
  ///
  /// In zh, this message translates to:
  /// **'请输入邮箱'**
  String get emailRequired;

  /// No description provided for @emailInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入有效的邮箱'**
  String get emailInvalid;

  /// No description provided for @password.
  ///
  /// In zh, this message translates to:
  /// **'密码'**
  String get password;

  /// No description provided for @passwordRequired.
  ///
  /// In zh, this message translates to:
  /// **'请输入密码'**
  String get passwordRequired;

  /// No description provided for @showPassword.
  ///
  /// In zh, this message translates to:
  /// **'显示密码'**
  String get showPassword;

  /// No description provided for @hidePassword.
  ///
  /// In zh, this message translates to:
  /// **'隐藏密码'**
  String get hidePassword;

  /// No description provided for @login.
  ///
  /// In zh, this message translates to:
  /// **'登录'**
  String get login;

  /// No description provided for @loginFailed.
  ///
  /// In zh, this message translates to:
  /// **'登录失败，请稍后重试'**
  String get loginFailed;

  /// No description provided for @sessionRestoreFailed.
  ///
  /// In zh, this message translates to:
  /// **'无法恢复登录状态，请检查网络后重试'**
  String get sessionRestoreFailed;

  /// No description provided for @retry.
  ///
  /// In zh, this message translates to:
  /// **'重试'**
  String get retry;

  /// No description provided for @loggedInAccount.
  ///
  /// In zh, this message translates to:
  /// **'当前登录账户'**
  String get loggedInAccount;

  /// No description provided for @logout.
  ///
  /// In zh, this message translates to:
  /// **'退出登录'**
  String get logout;

  /// No description provided for @logoutRevocationFailed.
  ///
  /// In zh, this message translates to:
  /// **'已退出本机，但服务器会话撤销失败'**
  String get logoutRevocationFailed;

  /// No description provided for @createAccount.
  ///
  /// In zh, this message translates to:
  /// **'创建账户'**
  String get createAccount;

  /// No description provided for @forgotPassword.
  ///
  /// In zh, this message translates to:
  /// **'忘记密码'**
  String get forgotPassword;

  /// No description provided for @resetPassword.
  ///
  /// In zh, this message translates to:
  /// **'重置密码'**
  String get resetPassword;

  /// No description provided for @registerDescription.
  ///
  /// In zh, this message translates to:
  /// **'使用邮箱验证码创建你的 Fund Keeper 账户'**
  String get registerDescription;

  /// No description provided for @resetPasswordDescription.
  ///
  /// In zh, this message translates to:
  /// **'验证邮箱后设置新密码，旧登录状态将失效'**
  String get resetPasswordDescription;

  /// No description provided for @verificationCode.
  ///
  /// In zh, this message translates to:
  /// **'验证码'**
  String get verificationCode;

  /// No description provided for @verificationCodeInvalid.
  ///
  /// In zh, this message translates to:
  /// **'请输入6位数字验证码'**
  String get verificationCodeInvalid;

  /// No description provided for @sendCode.
  ///
  /// In zh, this message translates to:
  /// **'发送验证码'**
  String get sendCode;

  /// No description provided for @resendInSeconds.
  ///
  /// In zh, this message translates to:
  /// **'{seconds}秒'**
  String resendInSeconds(int seconds);

  /// No description provided for @codeSentHint.
  ///
  /// In zh, this message translates to:
  /// **'如果该邮箱可用于此操作，验证码邮件将会发送'**
  String get codeSentHint;

  /// No description provided for @newPassword.
  ///
  /// In zh, this message translates to:
  /// **'新密码'**
  String get newPassword;

  /// No description provided for @confirmPassword.
  ///
  /// In zh, this message translates to:
  /// **'确认密码'**
  String get confirmPassword;

  /// No description provided for @passwordTooShort.
  ///
  /// In zh, this message translates to:
  /// **'密码至少需要8个字符'**
  String get passwordTooShort;

  /// No description provided for @passwordTooLong.
  ///
  /// In zh, this message translates to:
  /// **'密码编码后不能超过72字节'**
  String get passwordTooLong;

  /// No description provided for @passwordsDoNotMatch.
  ///
  /// In zh, this message translates to:
  /// **'两次输入的密码不一致'**
  String get passwordsDoNotMatch;

  /// No description provided for @confirmReset.
  ///
  /// In zh, this message translates to:
  /// **'确认重置'**
  String get confirmReset;

  /// No description provided for @backToLogin.
  ///
  /// In zh, this message translates to:
  /// **'返回登录'**
  String get backToLogin;

  /// No description provided for @registrationCompleted.
  ///
  /// In zh, this message translates to:
  /// **'账户创建成功，请登录'**
  String get registrationCompleted;

  /// No description provided for @passwordResetCompleted.
  ///
  /// In zh, this message translates to:
  /// **'密码已重置，请重新登录'**
  String get passwordResetCompleted;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
