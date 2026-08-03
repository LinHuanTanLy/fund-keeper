import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/design_system/app_theme.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/features/transaction/presentation/transaction_history_page.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

import '../../../support/portfolio_fakes.dart';
import '../../../support/transaction_fakes.dart';

void main() {
  testWidgets('renders filters without overflow on a 394dp wide screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(394, 853);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final transactions = FakeTransactionRemoteDataSource(
      records: [
        transactionFixture(id: 'buy-1', type: 'BUY', status: 'CONFIRMED'),
      ],
    );

    await _pumpPage(tester, transactions);

    expect(
      find.byKey(const Key('transaction-account-filter-__all__')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('loads another page and applies the transaction type filter', (
    tester,
  ) async {
    final transactions = FakeTransactionRemoteDataSource(
      pageSize: 1,
      records: [
        transactionFixture(
          id: 'buy-1',
          type: 'BUY',
          status: 'CONFIRMED',
          fundName: '买入基金',
        ),
        transactionFixture(
          id: 'sell-1',
          type: 'SELL',
          status: 'CONFIRMED',
          fundName: '卖出基金',
        ),
      ],
    );
    await _pumpPage(tester, transactions);

    expect(find.byKey(const Key('transaction-buy-1')), findsOneWidget);
    expect(find.byKey(const Key('transaction-sell-1')), findsNothing);

    await _tapVisible(tester, find.byKey(const Key('transaction-load-more')));
    await tester.pumpAndSettle();

    expect(transactions.requestedPages, [0, 1]);
    expect(find.byKey(const Key('transaction-buy-1')), findsOneWidget);
    expect(find.byKey(const Key('transaction-sell-1')), findsOneWidget);

    await tester.tap(find.byKey(const Key('transaction-type-filter-__all__')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('买入').last);
    await tester.pumpAndSettle();

    expect(transactions.lastFilters?.type, 'BUY');
    expect(transactions.requestedPages.last, 0);
    expect(find.byKey(const Key('transaction-buy-1')), findsOneWidget);
    expect(find.byKey(const Key('transaction-sell-1')), findsNothing);
  });

  testWidgets('confirms a pending buy and refreshes the record', (
    tester,
  ) async {
    final transactions = FakeTransactionRemoteDataSource(
      records: [
        transactionFixture(id: 'buy-pending', type: 'BUY', status: 'PENDING'),
      ],
    );
    await _pumpPage(tester, transactions);

    await _tapVisible(
      tester,
      find.byKey(const Key('transaction-confirm-buy-pending')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('buy-confirm-shares')),
      '486.12345678',
    );
    await tester.tap(
      find.byKey(const Key('transaction-confirm-submit')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();

    expect(transactions.confirmBuyCalls, 1);
    expect(
      transactions.lastBuyDraft?.confirmedShares,
      Decimal.parse('486.12345678'),
    );
    expect(find.text('交易已确认'), findsOneWidget);
    expect(
      find.byKey(const Key('transaction-confirm-buy-pending')),
      findsNothing,
    );
  });

  testWidgets('requires received amount and shares for a partial sell', (
    tester,
  ) async {
    final transactions = FakeTransactionRemoteDataSource(
      records: [
        transactionFixture(
          id: 'sell-pending',
          type: 'SELL',
          sellMode: 'PARTIAL',
          status: 'ESTIMATED',
        ),
      ],
    );
    await _pumpPage(tester, transactions);

    await _tapVisible(
      tester,
      find.byKey(const Key('transaction-confirm-sell-pending')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('sell-confirm-received')),
      '798.50',
    );
    await tester.enterText(
      find.byKey(const Key('sell-confirm-shares')),
      '320.12345678',
    );
    await tester.tap(
      find.byKey(const Key('transaction-confirm-submit')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();

    expect(transactions.confirmSellCalls, 1);
    expect(
      transactions.lastSellDraft?.actualReceivedAmount,
      Decimal.parse('798.50'),
    );
    expect(
      transactions.lastSellDraft?.confirmedShares,
      Decimal.parse('320.12345678'),
    );
    expect(find.text('交易已确认'), findsOneWidget);
  });

  testWidgets('cancels an estimated transaction with an optional reason', (
    tester,
  ) async {
    final transactions = FakeTransactionRemoteDataSource(
      records: [
        transactionFixture(
          id: 'buy-estimated',
          type: 'BUY',
          status: 'ESTIMATED',
        ),
      ],
    );
    await _pumpPage(tester, transactions);

    await _tapVisible(
      tester,
      find.byKey(const Key('transaction-cancel-buy-estimated')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('transaction-cancel-reason')),
      '平台最终未成交',
    );
    await tester.tap(find.byKey(const Key('transaction-cancel-submit')));
    await tester.pumpAndSettle();

    expect(transactions.cancelCalls, 1);
    expect(transactions.lastCancellationReason, '平台最终未成交');
    expect(find.text('交易已标记为未完成'), findsOneWidget);
    expect(find.text('已撤销'), findsOneWidget);
    expect(
      find.byKey(const Key('transaction-cancel-buy-estimated')),
      findsNothing,
    );
  });
}

Future<void> _pumpPage(
  WidgetTester tester,
  FakeTransactionRemoteDataSource transactions,
) async {
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
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        transactionRemoteDataSourceProvider.overrideWithValue(transactions),
        portfolioRemoteDataSourceProvider.overrideWithValue(portfolio),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: TransactionHistoryPage()),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  final list = find.byKey(const Key('transaction-history-list'));
  for (var attempt = 0; attempt < 10 && finder.evaluate().isEmpty; attempt++) {
    await tester.drag(list, const Offset(0, -260));
    await tester.pumpAndSettle();
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
}
