import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/app/app.dart';
import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

import '../../../support/auth_fakes.dart';

void main() {
  final environment = AppEnvironment(
    flavor: AppFlavor.dev,
    apiBaseUrl: 'http://127.0.0.1:8080',
    pollingIntervalSeconds: 30,
    requestTimeoutSeconds: 15,
    enableNetworkLogs: false,
  );

  Future<void> pumpApp(
    WidgetTester tester,
    FakeAuthRemoteDataSource remote,
  ) async {
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
  }

  Future<void> fillCredentialForm(
    WidgetTester tester, {
    String email = 'new@example.com',
    String code = '123456',
    String password = 'password-123',
  }) async {
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), email);
    await tester.enterText(fields.at(1), code);
    await tester.enterText(fields.at(2), password);
    await tester.enterText(fields.at(3), password);
  }

  testWidgets('registration sends REGISTER code and returns to login', (
    tester,
  ) async {
    final remote = FakeAuthRemoteDataSource();
    await pumpApp(tester, remote);

    await tester.tap(find.text('创建账户'));
    await tester.pumpAndSettle();
    await fillCredentialForm(tester);

    await tester.tap(find.widgetWithText(OutlinedButton, '发送验证码'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(remote.requestCodeCalls, 1);
    expect(remote.lastCodePurpose, EmailCodePurpose.register);
    expect(find.text('60秒'), findsOneWidget);
    expect(
      tester
          .widget<OutlinedButton>(find.widgetWithText(OutlinedButton, '60秒'))
          .onPressed,
      isNull,
    );
    await tester.enterText(find.byType(TextFormField).at(1), '123456');

    await tester.tap(find.widgetWithText(FilledButton, '创建账户'));
    await tester.pumpAndSettle();

    expect(remote.registerCalls, 1);
    expect(remote.lastCode, '123456');
    expect(remote.lastPassword, 'password-123');
    expect(find.text('账户创建成功，请登录'), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byType(TextFormField).first)
          .controller
          ?.text,
      'new@example.com',
    );
  });

  testWidgets('password reset uses RESET_PASSWORD and returns to login', (
    tester,
  ) async {
    final remote = FakeAuthRemoteDataSource();
    await pumpApp(tester, remote);

    await tester.tap(find.text('忘记密码'));
    await tester.pumpAndSettle();
    await fillCredentialForm(tester);

    await tester.tap(find.widgetWithText(OutlinedButton, '发送验证码'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(remote.lastCodePurpose, EmailCodePurpose.resetPassword);
    await tester.enterText(find.byType(TextFormField).at(1), '123456');

    await tester.tap(find.widgetWithText(FilledButton, '确认重置'));
    await tester.pumpAndSettle();

    expect(remote.resetPasswordCalls, 1);
    expect(remote.lastCode, '123456');
    expect(remote.lastPassword, 'password-123');
    expect(find.text('密码已重置，请重新登录'), findsOneWidget);
  });

  testWidgets('failed registration retains email/code but clears passwords', (
    tester,
  ) async {
    final remote = FakeAuthRemoteDataSource()
      ..registerFailure = const BusinessFailure(
        message: '验证码错误或已过期',
        code: 'INVALID_EMAIL_CODE',
      );
    await pumpApp(tester, remote);

    await tester.tap(find.text('创建账户'));
    await tester.pumpAndSettle();
    await fillCredentialForm(tester);
    await tester.tap(find.widgetWithText(FilledButton, '创建账户'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    expect(find.text('验证码错误或已过期'), findsOneWidget);
    expect(
      tester.widget<TextFormField>(fields.at(0)).controller?.text,
      'new@example.com',
    );
    expect(
      tester.widget<TextFormField>(fields.at(1)).controller?.text,
      '123456',
    );
    expect(
      tester.widget<TextFormField>(fields.at(2)).controller?.text,
      isEmpty,
    );
    expect(
      tester.widget<TextFormField>(fields.at(3)).controller?.text,
      isEmpty,
    );
  });

  testWidgets('password policy blocks submission before the network call', (
    tester,
  ) async {
    final remote = FakeAuthRemoteDataSource();
    await pumpApp(tester, remote);

    await tester.tap(find.text('创建账户'));
    await tester.pumpAndSettle();
    await fillCredentialForm(tester, password: '1234567');
    await tester.tap(find.widgetWithText(FilledButton, '创建账户'));
    await tester.pump();

    expect(find.text('密码至少需要8个字符'), findsOneWidget);
    expect(remote.registerCalls, 0);
  });
}
