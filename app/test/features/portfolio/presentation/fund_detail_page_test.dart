import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/data/portfolio_repository.dart';
import 'package:fund_keeper/features/portfolio/presentation/fund_detail_page.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

import '../../../support/portfolio_fakes.dart';

void main() {
  testWidgets('renders summary account and transaction detail', (tester) async {
    final remote = FakePortfolioRemoteDataSource();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          portfolioRepositoryProvider.overrideWithValue(
            PortfolioRepository(remote),
          ),
        ],
        child: const MaterialApp(
          locale: Locale('zh'),
          localizationsDelegates: [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: FundDetailPage(fundCode: '510300'),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(remote.lastDetailFundCode, '510300');
    expect(find.byKey(const Key('fund-detail-summary')), findsOneWidget);
    expect(find.text('沪深300ETF'), findsOneWidget);
    expect(
      find.byKey(const Key('fund-detail-account-account-1')),
      findsOneWidget,
    );
    expect(find.text('默认账户'), findsWidgets);
    expect(find.textContaining('场内 ETF 实时行情'), findsWidgets);

    await tester.scrollUntilVisible(
      find.byKey(const Key('fund-detail-transaction-transaction-1')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(
      find.byKey(const Key('fund-detail-transaction-transaction-1')),
      findsOneWidget,
    );
  });
}
