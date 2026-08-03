import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/design_system/app_theme.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/entry/application/entry_providers.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';
import 'package:fund_keeper/features/entry/presentation/json_import_page.dart';
import 'package:fund_keeper/features/entry/presentation/manual_buy_page.dart';
import 'package:fund_keeper/features/entry/presentation/manual_sell_page.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

import '../../../support/entry_fakes.dart';
import '../../../support/portfolio_fakes.dart';

void main() {
  testWidgets('manual buy retries with the same idempotency request id', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..manualBuyFailure = const NetworkFailure(message: '请求超时');
    final portfolio = FakePortfolioRemoteDataSource(
      accounts: const [
        PortfolioAccount(
          id: 'account-1',
          name: '默认账户',
          platform: 'OTHER',
          status: 'ACTIVE',
        ),
      ],
    );
    await _pump(
      tester,
      const ManualBuyPage(),
      entry: entry,
      portfolio: portfolio,
    );

    await tester.enterText(
      find.byKey(const Key('manual-buy-fund-code')),
      '005827',
    );
    await tester.enterText(
      find.byKey(const Key('manual-buy-amount')),
      '1000.50',
    );
    await tester.tap(find.text('15:00 后'));
    await tester.pump();
    await _tapVisible(
      tester,
      find.byKey(const Key('manual-buy-submit')),
      scrollable: find.byKey(const Key('manual-buy-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('请求超时'), findsOneWidget);
    expect(entry.manualBuyCalls, 1);

    await _tapVisible(
      tester,
      find.byKey(const Key('manual-buy-submit')),
      scrollable: find.byKey(const Key('manual-buy-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('买入记录已创建'), findsOneWidget);
    expect(entry.manualBuyCalls, 2);
    expect(
      entry.manualBuyDrafts[0].requestId,
      entry.manualBuyDrafts[1].requestId,
    );
    expect(entry.manualBuyDrafts.last.submittedPeriod, SubmittedPeriod.after15);
  });

  testWidgets('JSON import preflights before commit and retries same batch', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..commitFailure = const NetworkFailure(message: '确认请求超时');
    await _pump(
      tester,
      const JsonImportPage(),
      entry: entry,
      portfolio: FakePortfolioRemoteDataSource(),
    );

    await tester.tap(find.text('交易流水'));
    await tester.pump();
    const raw = '{"importType":"TRANSACTION_BATCH","batchId":"batch-001"}';
    await tester.enterText(find.byKey(const Key('json-import-content')), raw);
    await tester.tap(find.byKey(const Key('json-import-preflight')));
    await tester.pumpAndSettle();

    expect(find.text('预检通过，可以确认导入'), findsOneWidget);
    expect(entry.preflightCalls, 1);
    expect(entry.commitCalls, 0);
    expect(entry.lastPreflightKind, ImportKind.transactionBatch);
    expect(entry.lastRawJson, raw);

    await _tapVisible(
      tester,
      find.byKey(const Key('json-import-commit')),
      scrollable: find.byKey(const Key('json-import-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('确认请求超时'), findsOneWidget);
    expect(find.text('重试确认'), findsOneWidget);
    expect(entry.commitCalls, 1);
    expect(entry.preflightCalls, 1);

    await _tapVisible(
      tester,
      find.byKey(const Key('json-import-commit')),
      scrollable: find.byKey(const Key('json-import-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('批量导入成功'), findsOneWidget);
    expect(entry.commitCalls, 2);
    expect(entry.lastCommitKind, ImportKind.transactionBatch);
    expect(entry.lastCommitBatchId, 'batch-001');
    expect(entry.preflightCalls, 1);
  });

  testWidgets('failed preflight exposes issues and cannot commit', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..preflightResult = importPreflightFixture(
        status: 'PREFLIGHT_FAILED',
        errorCount: 1,
      );
    await _pump(
      tester,
      const JsonImportPage(),
      entry: entry,
      portfolio: FakePortfolioRemoteDataSource(),
    );

    await tester.enterText(
      find.byKey(const Key('json-import-content')),
      '{"importType":"POSITION_SNAPSHOT"}',
    );
    await tester.tap(find.byKey(const Key('json-import-preflight')));
    await tester.pumpAndSettle();

    expect(find.text('预检未通过，请修复 JSON 后重试'), findsOneWidget);
    expect(find.textContaining('基金不存在'), findsOneWidget);
    expect(find.byKey(const Key('json-import-commit')), findsNothing);
    expect(entry.commitCalls, 0);
  });

  testWidgets('snapshot calibration shows current target and differences', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..preflightResult = importPreflightFixture(needsCalibration: true);
    await _pump(
      tester,
      const JsonImportPage(),
      entry: entry,
      portfolio: FakePortfolioRemoteDataSource(),
    );

    await tester.enterText(
      find.byKey(const Key('json-import-content')),
      '{"importType":"POSITION_SNAPSHOT"}',
    );
    await tester.tap(find.byKey(const Key('json-import-preflight')));
    await tester.pumpAndSettle();

    expect(find.textContaining('请核对持仓变更'), findsOneWidget);
    expect(find.byKey(const Key('json-import-calibration-1')), findsOneWidget);
    expect(find.text('确认后将校准当前持仓'), findsOneWidget);
    expect(find.text('份额变化 +10'), findsOneWidget);
    expect(find.text('成本变化 +¥30.00'), findsOneWidget);
    expect(find.text('持有起始日将变化'), findsOneWidget);
    expect(find.byKey(const Key('json-import-commit')), findsOneWidget);
    expect(entry.commitCalls, 0);
  });

  testWidgets('full snapshot clearly warns before clearing a position', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..preflightResult = importPreflightFixture(clearsPosition: true);
    await _pump(
      tester,
      const JsonImportPage(),
      entry: entry,
      portfolio: FakePortfolioRemoteDataSource(),
    );

    await tester.enterText(
      find.byKey(const Key('json-import-content')),
      '{"importType":"POSITION_SNAPSHOT","snapshotMode":"FULL_ACCOUNT"}',
    );
    await tester.tap(find.byKey(const Key('json-import-preflight')));
    await tester.pumpAndSettle();

    expect(find.text('确认后将清空当前持仓'), findsOneWidget);
    expect(find.text('份额变化 -50'), findsOneWidget);
    expect(find.text('成本变化 -¥90.00'), findsOneWidget);
    expect(find.text('无持仓'), findsOneWidget);
    expect(entry.commitCalls, 0);
  });

  testWidgets('business conflict disables commit until a new preflight', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..commitFailure = const BusinessFailure(
        message: '账户或持仓已发生变化',
        code: 'IMPORT_PREFLIGHT_STALE',
      );
    await _pump(
      tester,
      const JsonImportPage(),
      entry: entry,
      portfolio: FakePortfolioRemoteDataSource(),
    );

    await tester.enterText(
      find.byKey(const Key('json-import-content')),
      '{"importType":"POSITION_SNAPSHOT","batchId":"batch-001"}',
    );
    await tester.tap(find.byKey(const Key('json-import-preflight')));
    await tester.pumpAndSettle();
    await _tapVisible(
      tester,
      find.byKey(const Key('json-import-commit')),
      scrollable: find.byKey(const Key('json-import-list')),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('服务端状态可能已变化'), findsOneWidget);
    final commitButton = tester.widget<FilledButton>(
      find.byKey(const Key('json-import-commit')),
    );
    expect(commitButton.onPressed, isNull);
    expect(entry.preflightCalls, 1);
    expect(entry.commitCalls, 1);
  });

  testWidgets('manual partial sell retries with the same idempotency id', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource()
      ..manualSellFailure = const NetworkFailure(message: '请求超时');
    final portfolio = FakePortfolioRemoteDataSource(
      accounts: const [
        PortfolioAccount(
          id: 'account-1',
          name: '默认账户',
          platform: 'OTHER',
          status: 'ACTIVE',
        ),
      ],
      funds: [
        fundCardFixture(
          code: '005827',
          name: '易方达蓝筹精选混合',
          theme: 'MIXED',
          totalShares: 1000,
          marketValue: 2200,
        ),
      ],
    );
    await _pump(
      tester,
      const ManualSellPage(),
      entry: entry,
      portfolio: portfolio,
    );

    await tester.enterText(
      find.byKey(const Key('manual-sell-expected-amount')),
      '800.50',
    );
    await tester.enterText(
      find.byKey(const Key('manual-sell-note')),
      '支付宝部分卖出',
    );
    await _tapVisible(
      tester,
      find.byKey(const Key('manual-sell-submit')),
      scrollable: find.byKey(const Key('manual-sell-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('请求超时'), findsOneWidget);
    expect(entry.manualSellCalls, 1);

    await _tapVisible(
      tester,
      find.byKey(const Key('manual-sell-submit')),
      scrollable: find.byKey(const Key('manual-sell-list')),
    );
    await tester.pumpAndSettle();

    expect(find.text('卖出记录已创建'), findsOneWidget);
    expect(entry.manualSellCalls, 2);
    expect(
      entry.manualSellDrafts[0].requestId,
      entry.manualSellDrafts[1].requestId,
    );
    expect(entry.manualSellDrafts.last.accountId, 'account-1');
    expect(entry.manualSellDrafts.last.fundCode, '005827');
    expect(entry.manualSellDrafts.last.sellMode, SellMode.partial);
    expect(entry.manualSellDrafts.last.expectedAmount, Decimal.parse('800.50'));
    expect(entry.manualSellDrafts.last.note, '支付宝部分卖出');
  });

  testWidgets('manual full sell does not require an expected amount', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource();
    final portfolio = FakePortfolioRemoteDataSource(
      accounts: const [
        PortfolioAccount(
          id: 'account-1',
          name: '默认账户',
          platform: 'OTHER',
          status: 'ACTIVE',
        ),
      ],
      funds: [
        fundCardFixture(code: '005827', name: '易方达蓝筹精选混合', theme: 'MIXED'),
      ],
    );
    await _pump(
      tester,
      const ManualSellPage(),
      entry: entry,
      portfolio: portfolio,
    );

    await tester.tap(find.text('全部卖出'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('manual-sell-expected-amount')), findsNothing);
    await _tapVisible(
      tester,
      find.byKey(const Key('manual-sell-submit')),
      scrollable: find.byKey(const Key('manual-sell-list')),
    );
    await tester.pumpAndSettle();

    expect(entry.manualSellCalls, 1);
    expect(entry.manualSellDrafts.single.sellMode, SellMode.full);
    expect(entry.manualSellDrafts.single.expectedAmount, isNull);
    expect(find.text('卖出记录已创建'), findsOneWidget);
  });

  testWidgets('manual sell is blocked when the fund has an open sell', (
    tester,
  ) async {
    final entry = FakeEntryRemoteDataSource();
    final portfolio = FakePortfolioRemoteDataSource(
      accounts: const [
        PortfolioAccount(
          id: 'account-1',
          name: '默认账户',
          platform: 'OTHER',
          status: 'ACTIVE',
        ),
      ],
      funds: [
        fundCardFixture(
          code: '005827',
          name: '易方达蓝筹精选混合',
          theme: 'MIXED',
          openSellCount: 1,
        ),
      ],
    );
    await _pump(
      tester,
      const ManualSellPage(),
      entry: entry,
      portfolio: portfolio,
    );

    expect(find.byKey(const Key('manual-sell-open-blocked')), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('manual-sell-submit')));
    final button = tester.widget<FilledButton>(
      find.byKey(const Key('manual-sell-submit')),
    );
    expect(button.onPressed, isNull);
    expect(entry.manualSellCalls, 0);
  });
}

Future<void> _pump(
  WidgetTester tester,
  Widget page, {
  required FakeEntryRemoteDataSource entry,
  required FakePortfolioRemoteDataSource portfolio,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        entryRemoteDataSourceProvider.overrideWithValue(entry),
        portfolioRemoteDataSourceProvider.overrideWithValue(portfolio),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: page,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tapVisible(
  WidgetTester tester,
  Finder finder, {
  required Finder scrollable,
}) async {
  for (var attempt = 0; attempt < 8 && finder.evaluate().isEmpty; attempt++) {
    await tester.drag(scrollable, const Offset(0, -260));
    await tester.pumpAndSettle();
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
}
