// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'Fund Keeper';

  @override
  String get homeTab => '首页';

  @override
  String get recordsTab => '记录';

  @override
  String get profileTab => '我的';

  @override
  String get createAction => '新增';

  @override
  String get chooseEntryMethod => '选择录入方式';

  @override
  String get manualBuyTitle => '手动录入买入';

  @override
  String get manualBuyDescription => '只需填写账户、基金代码、购买金额和提交时间';

  @override
  String get manualSellTitle => '手动录入卖出';

  @override
  String get manualSellDescription => '从当前持仓选择基金，记录部分卖出或全部卖出';

  @override
  String get sellMode => '卖出方式';

  @override
  String get partialSell => '部分卖出';

  @override
  String get fullSell => '全部卖出';

  @override
  String get holdingFund => '持仓基金';

  @override
  String get selectHoldingFund => '请选择要卖出的持仓基金';

  @override
  String get noSellablePosition => '该账户暂无可卖出的基金持仓';

  @override
  String get positionsLoadFailed => '持仓基金加载失败';

  @override
  String get positionShares => '当前份额';

  @override
  String get positionMarketValue => '当前市值';

  @override
  String get expectedReceivedAmount => '预计到账金额';

  @override
  String get expectedReceivedAmountHint => '例如 2000.00';

  @override
  String get partialSellHint => '只需填写预计到账金额，平台确认份额和实际到账金额可稍后补充。';

  @override
  String get fullSellHint => '先创建待确认的全部卖出记录，平台最终到账后再补充结果。';

  @override
  String get sellNote => '备注（可选）';

  @override
  String get sellNoteHint => '例如：支付宝部分卖出';

  @override
  String get openSellBlocked => '该基金已有待处理卖出，请先确认或标记原交易未完成。';

  @override
  String get submitSell => '确认录入卖出';

  @override
  String get manualSellSuccess => '卖出记录已创建';

  @override
  String get estimatedSoldShares => '预计减少份额';

  @override
  String get sellResultHint => '平台最终到账金额及确认份额可在交易记录中补充。';

  @override
  String get jsonImportTitle => 'JSON 批量导入';

  @override
  String get jsonImportDescription => '先预检逐行结果，确认后才写入持仓';

  @override
  String get platformAccount => '平台账户';

  @override
  String get selectAccount => '请选择平台账户';

  @override
  String get noActiveAccount => '暂无可用账户，请先在“我的”中创建账户';

  @override
  String get fundCode => '基金代码';

  @override
  String get fundCodeHint => '6 位数字，例如 005827';

  @override
  String get fundCodeInvalid => '请输入6位基金代码';

  @override
  String get purchaseAmount => '购买金额';

  @override
  String get amountHint => '例如 5000.00';

  @override
  String get amountInvalid => '请输入大于0、最多4位小数的金额';

  @override
  String get submittedDate => '提交日期';

  @override
  String get submittedPeriod => '提交时段';

  @override
  String get before15 => '15:00 前';

  @override
  String get after15 => '15:00 后';

  @override
  String get submitBuy => '确认录入';

  @override
  String get submitting => '正在提交';

  @override
  String get manualBuySuccess => '买入记录已创建';

  @override
  String get transactionStatus => '交易状态';

  @override
  String get statusEstimated => '估算持仓';

  @override
  String get statusPending => '等待净值或确认';

  @override
  String get statusConfirmed => '平台已确认';

  @override
  String get effectiveTradeDate => '有效交易日';

  @override
  String get estimatedShares => '估算份额';

  @override
  String get complete => '完成';

  @override
  String get entryFailed => '录入失败，请稍后重试';

  @override
  String get importKind => '导入类型';

  @override
  String get positionSnapshot => '持仓快照';

  @override
  String get transactionBatch => '交易流水';

  @override
  String get positionSnapshotHint =>
      '用于录入当前持仓；PARTIAL 不影响未列出的基金，FULL_ACCOUNT 可能清空未列出的持仓。';

  @override
  String get transactionBatchHint => '用于按顺序导入买入或卖出流水，受已有快照时间边界约束。';

  @override
  String get jsonContent => 'JSON 内容';

  @override
  String get jsonContentHint => '粘贴完整 JSON，importType 必须与上方选择一致';

  @override
  String get jsonRequired => '请粘贴 JSON 内容';

  @override
  String get preflight => '开始预检';

  @override
  String get preflighting => '正在预检';

  @override
  String get preflightResult => '预检结果';

  @override
  String get readyToCommit => '预检通过，可以确认导入';

  @override
  String get preflightFailed => '预检未通过，请修复 JSON 后重试';

  @override
  String get alreadyCommitted => '该批次已经成功导入';

  @override
  String get batchId => '批次 ID';

  @override
  String get totalRows => '总行数';

  @override
  String get importableRows => '可导入';

  @override
  String get warnings => '警告';

  @override
  String get errors => '错误';

  @override
  String get calibrations => '需校准';

  @override
  String get willCreateAccount => '确认时将创建账户';

  @override
  String rowNumber(int row) {
    return '第 $row 行';
  }

  @override
  String get confirmImport => '确认整批导入';

  @override
  String get retryCommit => '重试确认';

  @override
  String get committing => '正在确认';

  @override
  String get importSuccess => '批量导入成功';

  @override
  String get appliedRows => '已处理行数';

  @override
  String get accountCreated => '平台账户已创建';

  @override
  String get repreflightRequired => '服务端状态可能已变化，请重新预检';

  @override
  String get importFailed => '导入失败，请稍后重试';

  @override
  String get homeFoundationTitle => '还没有基金持仓';

  @override
  String get homeFoundationDescription => '可以逐只录入基金，也可以通过 JSON 批量导入';

  @override
  String get allAccounts => '全部账户';

  @override
  String get totalAssets => '总资产';

  @override
  String get holdingCost => '持仓成本';

  @override
  String get cumulativeProfit => '累计收益';

  @override
  String get todayEstimatedProfit => '今日收益';

  @override
  String get holdingFunds => '持仓基金';

  @override
  String get valuationStatus => '行情与净值';

  @override
  String get refreshValuation => '刷新行情';

  @override
  String get lastUpdated => '更新时间';

  @override
  String get valuationLive => '场内 ETF 实时行情';

  @override
  String get valuationDelayed => '场内 ETF 行情延迟';

  @override
  String get valuationStale => '场内 ETF 行情已过期';

  @override
  String get valuationOfficial => '场外基金正式净值';

  @override
  String get valuationMixed => 'ETF 行情 + 场外正式净值';

  @override
  String get valuationMarketClosed => '场内 ETF 已收盘';

  @override
  String get valuationUnavailable => '行情或净值不可用';

  @override
  String get valuationPartial => '部分基金暂无行情或正式净值，汇总数据不完整';

  @override
  String get todayEstimatePartial => '部分基金暂无今日收益数据';

  @override
  String get containsEstimatedData => '包含尚未确认的估算持仓';

  @override
  String get themeDistribution => '板块分布';

  @override
  String get themeDistributionHint => '按当前市值计算，点击板块筛选基金';

  @override
  String get themeNoData => '暂无可用于板块分布的市值数据';

  @override
  String get themeAll => '全部';

  @override
  String get themeSemiconductor => '半导体';

  @override
  String get themeInternet => '互联网';

  @override
  String get themeConsumer => '消费';

  @override
  String get themeHealthcare => '医疗健康';

  @override
  String get themeNewEnergy => '新能源';

  @override
  String get themeBroadIndex => '宽基指数';

  @override
  String get themeFinance => '金融';

  @override
  String get themeOverseas => '海外';

  @override
  String get themeMixed => '混合';

  @override
  String get themeOther => '其他';

  @override
  String get myFunds => '我的基金';

  @override
  String get holdingAmount => '持仓金额';

  @override
  String get holdingProfit => '持仓收益';

  @override
  String get todayProfit => '今日收益';

  @override
  String get holdingDays => '持有天数';

  @override
  String get pendingBuy => '待确认买入';

  @override
  String get pendingConfirmation => '存在待确认交易';

  @override
  String get manualEntry => '手动录入';

  @override
  String get jsonImport => 'JSON 批量导入';

  @override
  String get portfolioLoadFailed => '持仓数据加载失败';

  @override
  String get fundDetailTitle => '基金详情';

  @override
  String get fundDetailLoadFailed => '基金详情加载失败';

  @override
  String get accountPositions => '账户持仓';

  @override
  String get openTransactions => '待处理交易';

  @override
  String get recentTransactions => '最近交易';

  @override
  String get noOpenTransactions => '暂无待处理交易';

  @override
  String get noRecentTransactions => '暂无交易记录';

  @override
  String get currentPrice => '当前价格/净值';

  @override
  String get fundShares => '持有份额';

  @override
  String get dataSource => '数据来源';

  @override
  String get accountsLoadFailed => '账户加载失败';

  @override
  String get recordsFoundationTitle => '交易记录将在后续业务切片接入';

  @override
  String get transactionRecords => '交易记录';

  @override
  String transactionRecordCount(int count) {
    return '共 $count 条';
  }

  @override
  String get allTypes => '全部类型';

  @override
  String get transactionType => '交易类型';

  @override
  String get allStatuses => '全部状态';

  @override
  String get transactionBuy => '买入';

  @override
  String get transactionSell => '卖出';

  @override
  String get transactionAdjustment => '持仓调整';

  @override
  String get statusNeedsCalibration => '需要校准';

  @override
  String get statusCancelled => '已撤销';

  @override
  String get statusReversed => '已冲正';

  @override
  String get fundCodeFilter => '按基金代码筛选';

  @override
  String get filter => '筛选';

  @override
  String get clearFilters => '清除筛选';

  @override
  String get transactionListEmpty => '没有符合条件的交易记录';

  @override
  String get transactionLoadFailed => '交易记录加载失败';

  @override
  String get loadMore => '加载更多';

  @override
  String get loadingMore => '正在加载';

  @override
  String get noMoreRecords => '没有更多记录了';

  @override
  String get submittedTime => '提交时间';

  @override
  String get receivedAmount => '实际到账';

  @override
  String get expectedAmount => '预计金额';

  @override
  String get realizedProfit => '已实现收益';

  @override
  String get confirmTransaction => '补充平台结果';

  @override
  String get confirmedShares => '平台确认份额';

  @override
  String get confirmedSharesHint => '最多8位小数';

  @override
  String get confirmedSharesInvalid => '请输入大于0、最多8位小数的份额';

  @override
  String get actualReceivedAmount => '实际到账金额';

  @override
  String get actualReceivedAmountInvalid => '请输入大于0、最多4位小数的金额';

  @override
  String get confirmedDate => '确认日期';

  @override
  String get confirm => '确认';

  @override
  String get cancelTransaction => '标记交易未完成';

  @override
  String get cancelTransactionDescription => '撤销后会恢复该笔预估交易对持仓的影响，交易记录仍会保留。';

  @override
  String get cancelReason => '原因（可选）';

  @override
  String get cancelReasonHint => '例如：平台最终未成交';

  @override
  String get transactionConfirmed => '交易已确认';

  @override
  String get transactionCancelled => '交易已标记为未完成';

  @override
  String get transactionActionFailed => '操作失败，请稍后重试';

  @override
  String get pendingOfficialNav => '等待正式净值';

  @override
  String get pendingFeeRule => '等待申购费率';

  @override
  String get pendingNavAndFee => '等待净值和费率';

  @override
  String get pendingSellConfirmation => '等待卖出确认';

  @override
  String get platformAccounts => '平台账户';

  @override
  String get platformAccountsDescription => '不同平台的同名基金会分别记录持仓和收益';

  @override
  String activeAccountCount(int count) {
    return '$count 个有效账户';
  }

  @override
  String get addPlatformAccount => '新增平台账户';

  @override
  String get editPlatformAccount => '修改平台账户';

  @override
  String get accountName => '账户名称';

  @override
  String get accountNameHint => '例如：我的支付宝';

  @override
  String get accountNameInvalid => '请输入1～100个字符的账户名称';

  @override
  String get accountPlatform => '平台类型';

  @override
  String get platformAlipay => '支付宝';

  @override
  String get platformTiantianFund => '天天基金';

  @override
  String get platformBank => '银行';

  @override
  String get platformOther => '其他';

  @override
  String get accountActive => '有效';

  @override
  String get accountArchived => '已归档';

  @override
  String get accountArchivedHint => '归档账户仅保留历史记录，不能继续录入交易';

  @override
  String get editAccount => '编辑账户';

  @override
  String get archiveAccount => '归档账户';

  @override
  String get archiveAccountDescription => '只有没有当前持仓和待确认交易的账户可以归档。归档后仍保留历史记录。';

  @override
  String get lastActiveAccountHint => '至少需要保留一个有效账户';

  @override
  String get save => '保存';

  @override
  String get accountUpdated => '平台账户已更新';

  @override
  String get accountArchivedSuccess => '平台账户已归档';

  @override
  String get accountActionFailed => '账户操作失败，请稍后重试';

  @override
  String get accountListLoadFailed => '平台账户加载失败';

  @override
  String get welcomeBack => '欢迎回来';

  @override
  String get loginDescription => '登录后查看和管理你的基金持仓';

  @override
  String get email => '邮箱';

  @override
  String get emailRequired => '请输入邮箱';

  @override
  String get emailInvalid => '请输入有效的邮箱';

  @override
  String get password => '密码';

  @override
  String get passwordRequired => '请输入密码';

  @override
  String get showPassword => '显示密码';

  @override
  String get hidePassword => '隐藏密码';

  @override
  String get login => '登录';

  @override
  String get loginFailed => '登录失败，请稍后重试';

  @override
  String get sessionRestoreFailed => '无法恢复登录状态，请检查网络后重试';

  @override
  String get retry => '重试';

  @override
  String get loggedInAccount => '当前登录账户';

  @override
  String get logout => '退出登录';

  @override
  String get logoutRevocationFailed => '已退出本机，但服务器会话撤销失败';

  @override
  String get createAccount => '创建账户';

  @override
  String get forgotPassword => '忘记密码';

  @override
  String get resetPassword => '重置密码';

  @override
  String get registerDescription => '使用邮箱验证码创建你的 Fund Keeper 账户';

  @override
  String get resetPasswordDescription => '验证邮箱后设置新密码，旧登录状态将失效';

  @override
  String get verificationCode => '验证码';

  @override
  String get verificationCodeInvalid => '请输入6位数字验证码';

  @override
  String get sendCode => '发送验证码';

  @override
  String resendInSeconds(int seconds) {
    return '$seconds秒';
  }

  @override
  String get codeSentHint => '如果该邮箱可用于此操作，验证码邮件将会发送';

  @override
  String get newPassword => '新密码';

  @override
  String get confirmPassword => '确认密码';

  @override
  String get passwordTooShort => '密码至少需要8个字符';

  @override
  String get passwordTooLong => '密码编码后不能超过72字节';

  @override
  String get passwordsDoNotMatch => '两次输入的密码不一致';

  @override
  String get confirmReset => '确认重置';

  @override
  String get backToLogin => '返回登录';

  @override
  String get registrationCompleted => '账户创建成功，请登录';

  @override
  String get passwordResetCompleted => '密码已重置，请重新登录';
}
