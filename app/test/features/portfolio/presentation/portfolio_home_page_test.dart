import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';
import 'package:fund_keeper/core/design_system/app_theme.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_home_page.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

import '../../../support/portfolio_fakes.dart';

void main() {
  final environment = AppEnvironment(
    flavor: AppFlavor.dev,
    apiBaseUrl: 'http://127.0.0.1:8080',
    pollingIntervalSeconds: 300,
    requestTimeoutSeconds: 15,
    enableNetworkLogs: false,
  );

  testWidgets('renders missing valuation as unavailable instead of zero', (
    tester,
  ) async {
    final remote = FakePortfolioRemoteDataSource(
      overview: portfolioOverviewFixture(
        valuationComplete: false,
        todayEstimateComplete: false,
        currentMarketValue: null,
        cumulativeProfit: null,
        todayEstimatedProfit: null,
        positionCount: 1,
      ),
      funds: [
        fundCardFixture(
          code: '012345',
          name: '无估值基金',
          theme: 'OTHER',
          marketValue: null,
          holdingProfit: null,
          todayProfit: null,
        ),
      ],
    );

    await _pumpHome(tester, environment, remote);

    final totalAssets = tester.widget<Text>(
      find.byKey(const Key('portfolio-total-assets')),
    );
    expect(totalAssets.data, '--');
    expect(find.text('部分基金暂无行情或正式净值，汇总数据不完整'), findsOneWidget);
    expect(find.text('¥0.00'), findsNothing);

    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('labels market quotes and official NAV by data source', (
    tester,
  ) async {
    final remote = FakePortfolioRemoteDataSource(
      overview: portfolioOverviewFixture(
        priceType: 'MIXED',
        valuationStatus: 'LIVE',
      ),
      funds: [
        fundCardFixture(
          code: '510300',
          name: '沪深300ETF',
          theme: 'BROAD_INDEX',
          priceType: 'MARKET',
          valuationStatus: 'LIVE',
        ),
        fundCardFixture(
          code: '005827',
          name: '易方达蓝筹精选混合',
          theme: 'MIXED',
          priceType: 'OFFICIAL',
          valuationStatus: 'OFFICIAL',
          observedAt: null,
        ),
      ],
    );

    await _pumpHome(tester, environment, remote);

    expect(find.text('ETF 行情 + 场外正式净值'), findsOneWidget);
    expect(find.textContaining('场内 ETF 实时行情'), findsOneWidget);
    expect(find.textContaining('场外基金正式净值'), findsOneWidget);

    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('theme selection filters visible fund cards', (tester) async {
    final remote = FakePortfolioRemoteDataSource(
      funds: [
        fundCardFixture(
          code: '000001',
          name: '芯片成长基金',
          theme: 'SEMICONDUCTOR',
          marketValue: 1000,
        ),
        fundCardFixture(
          code: '000002',
          name: '消费精选基金',
          theme: 'CONSUMER',
          marketValue: 2000,
        ),
      ],
    );

    await _pumpHome(tester, environment, remote);

    expect(find.byKey(const Key('fund-card-000001')), findsOneWidget);
    expect(find.byKey(const Key('fund-card-000002')), findsOneWidget);

    final semiconductorChip = find.byKey(const Key('theme-semiconductor'));
    await tester.drag(
      find.byKey(const Key('portfolio-home-list')),
      const Offset(0, -320),
    );
    await tester.pumpAndSettle();
    await tester.tap(semiconductorChip);
    await tester.pump();

    expect(find.byKey(const Key('fund-card-000001')), findsOneWidget);
    expect(find.byKey(const Key('fund-card-000002')), findsNothing);

    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('account selection reloads overview and funds with account id', (
    tester,
  ) async {
    final remote = FakePortfolioRemoteDataSource(
      accounts: const [
        PortfolioAccount(
          id: 'account-1',
          name: '支付宝',
          platform: 'ALIPAY',
          status: 'ACTIVE',
        ),
      ],
    );

    await _pumpHome(tester, environment, remote);

    await tester.tap(find.byKey(const Key('portfolio-account-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('支付宝').last);
    await tester.pumpAndSettle();

    expect(remote.lastOverviewAccountId, 'account-1');
    expect(remote.lastFundsAccountId, 'account-1');

    await tester.pumpWidget(const SizedBox());
  });
}

Future<void> _pumpHome(
  WidgetTester tester,
  AppEnvironment environment,
  FakePortfolioRemoteDataSource remote,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        appEnvironmentProvider.overrideWithValue(environment),
        portfolioRemoteDataSourceProvider.overrideWithValue(remote),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: PortfolioHomePage()),
      ),
    ),
  );
  await tester.pumpAndSettle();
}
