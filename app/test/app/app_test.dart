import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/app/app.dart';
import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/entry/presentation/manual_sell_page.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';

import '../support/auth_fakes.dart';
import '../support/portfolio_fakes.dart';

void main() {
  final environment = AppEnvironment(
    flavor: AppFlavor.dev,
    apiBaseUrl: 'http://127.0.0.1:8080',
    pollingIntervalSeconds: 30,
    requestTimeoutSeconds: 15,
    enableNetworkLogs: false,
  );

  testWidgets('redirects an unauthenticated launch to login', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appEnvironmentProvider.overrideWithValue(environment),
          authTokenStoreProvider.overrideWithValue(MemoryAuthTokenStore()),
          authRemoteDataSourceProvider.overrideWithValue(
            FakeAuthRemoteDataSource(),
          ),
          portfolioRemoteDataSourceProvider.overrideWithValue(
            FakePortfolioRemoteDataSource(),
          ),
        ],
        child: const FundKeeperApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('欢迎回来'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
    expect(find.text('首页'), findsNothing);
  });

  testWidgets('successful login opens the protected application shell', (
    tester,
  ) async {
    final store = MemoryAuthTokenStore();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appEnvironmentProvider.overrideWithValue(environment),
          authTokenStoreProvider.overrideWithValue(store),
          authRemoteDataSourceProvider.overrideWithValue(
            FakeAuthRemoteDataSource(),
          ),
          portfolioRemoteDataSourceProvider.overrideWithValue(
            FakePortfolioRemoteDataSource(),
          ),
        ],
        child: const FundKeeperApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byType(TextFormField).at(0),
      'user@example.com',
    );
    await tester.enterText(find.byType(TextFormField).at(1), 'password');
    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pumpAndSettle();

    expect(store.session?.user.email, 'user@example.com');
    expect(find.text('首页'), findsOneWidget);
    expect(find.text('记录'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);
    expect(find.byIcon(Icons.add_rounded), findsOneWidget);

    await tester.tap(find.byIcon(Icons.add_rounded));
    await tester.pumpAndSettle();
    expect(find.text('选择录入方式'), findsOneWidget);
    await tester.tap(find.text('手动录入卖出'));
    await tester.pumpAndSettle();
    expect(find.byType(ManualSellPage), findsOneWidget);
  });

  testWidgets('invalid credentials stay on login and show server feedback', (
    tester,
  ) async {
    final remote = FakeAuthRemoteDataSource()
      ..loginFailure = const AuthenticationFailure(
        message: '邮箱或密码错误',
        code: 'INVALID_CREDENTIALS',
      );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appEnvironmentProvider.overrideWithValue(environment),
          authTokenStoreProvider.overrideWithValue(MemoryAuthTokenStore()),
          authRemoteDataSourceProvider.overrideWithValue(remote),
        ],
        child: const FundKeeperApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byType(TextFormField).at(0),
      'user@example.com',
    );
    await tester.enterText(find.byType(TextFormField).at(1), 'wrong-password');
    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pumpAndSettle();

    expect(find.text('欢迎回来'), findsOneWidget);
    expect(find.text('邮箱或密码错误'), findsOneWidget);
    expect(find.text('首页'), findsNothing);
    expect(
      tester
          .widget<TextFormField>(find.byType(TextFormField).at(1))
          .controller
          ?.text,
      isEmpty,
    );
  });
}
