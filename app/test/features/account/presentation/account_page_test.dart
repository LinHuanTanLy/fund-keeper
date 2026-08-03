import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/design_system/app_theme.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/account/application/account_providers.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/account/presentation/account_page.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

import '../../../support/account_fakes.dart';
import '../../../support/auth_fakes.dart';

void main() {
  testWidgets('creates a platform account from the profile page', (
    tester,
  ) async {
    final remote = FakeAccountRemoteDataSource(
      accounts: [accountFixture(id: 'account-1', name: '默认账户')],
    );
    await _pumpPage(tester, remote);

    await tester.tap(find.byKey(const Key('account-add')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('account-editor-name')),
      '我的支付宝',
    );
    await tester.tap(find.byKey(const Key('account-editor-platform')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('支付宝').last);
    await _submitEditor(tester);

    expect(remote.createCalls, 1);
    expect(remote.lastCreateDraft?.name, '我的支付宝');
    expect(remote.lastCreateDraft?.platform, AccountPlatform.alipay);
    expect(find.text('平台账户已创建'), findsOneWidget);
    expect(find.text('我的支付宝'), findsOneWidget);
  });

  testWidgets('edits an active account name and platform', (tester) async {
    final remote = FakeAccountRemoteDataSource(
      accounts: [
        accountFixture(id: 'account-1', name: '默认账户'),
        accountFixture(id: 'account-2', name: '旧账户', platform: 'ALIPAY'),
      ],
    );
    await _pumpPage(tester, remote);

    await _tapVisible(tester, find.byKey(const Key('account-edit-account-2')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('account-editor-name')),
      '工资卡基金',
    );
    await tester.tap(find.byKey(const Key('account-editor-platform')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('银行').last);
    await _submitEditor(tester);

    expect(remote.updateCalls, 1);
    expect(remote.lastUpdateId, 'account-2');
    expect(remote.lastUpdateDraft?.name, '工资卡基金');
    expect(remote.lastUpdateDraft?.platform, AccountPlatform.bank);
    expect(find.text('平台账户已更新'), findsOneWidget);
    expect(find.text('工资卡基金'), findsOneWidget);
  });

  testWidgets('archives an account but protects the final active account', (
    tester,
  ) async {
    final remote = FakeAccountRemoteDataSource(
      accounts: [
        accountFixture(id: 'account-1', name: '默认账户'),
        accountFixture(id: 'account-2', name: '备用账户'),
      ],
    );
    await _pumpPage(tester, remote);

    await _tapVisible(
      tester,
      find.byKey(const Key('account-archive-account-2')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('account-archive-submit')));
    await tester.pumpAndSettle();

    expect(remote.archiveCalls, 1);
    expect(remote.lastArchiveId, 'account-2');
    expect(find.text('平台账户已归档'), findsOneWidget);
    expect(find.byKey(const Key('account-edit-account-2')), findsNothing);

    final lastArchiveButton = tester.widget<TextButton>(
      find.byKey(const Key('account-archive-account-1')),
    );
    expect(lastArchiveButton.onPressed, isNull);
    expect(find.text('至少需要保留一个有效账户'), findsOneWidget);
  });

  testWidgets('keeps an account active when the server rejects archival', (
    tester,
  ) async {
    final remote =
        FakeAccountRemoteDataSource(
            accounts: [
              accountFixture(id: 'account-1', name: '默认账户'),
              accountFixture(id: 'account-2', name: '有持仓账户'),
            ],
          )
          ..archiveFailure = const BusinessFailure(
            message: '存在当前持仓或待确认交易，不能归档',
            code: 'ACCOUNT_HAS_OPEN_ACTIVITY',
          );
    await _pumpPage(tester, remote);

    await _tapVisible(
      tester,
      find.byKey(const Key('account-archive-account-2')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('account-archive-submit')));
    await tester.pumpAndSettle();

    expect(remote.archiveCalls, 1);
    expect(find.text('存在当前持仓或待确认交易，不能归档'), findsOneWidget);
    expect(find.byKey(const Key('account-edit-account-2')), findsOneWidget);
    expect(remote.listCalls, greaterThanOrEqualTo(2));
  });
}

Future<void> _pumpPage(
  WidgetTester tester,
  FakeAccountRemoteDataSource accountRemote,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        accountRemoteDataSourceProvider.overrideWithValue(accountRemote),
        authTokenStoreProvider.overrideWithValue(
          MemoryAuthTokenStore(sampleSession()),
        ),
        authRemoteDataSourceProvider.overrideWithValue(
          FakeAuthRemoteDataSource(),
        ),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: AccountPage()),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  final list = find.byKey(const Key('account-management-list'));
  for (var attempt = 0; attempt < 8 && finder.evaluate().isEmpty; attempt++) {
    await tester.drag(list, const Offset(0, -220));
    await tester.pumpAndSettle();
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
}

Future<void> _submitEditor(WidgetTester tester) async {
  tester.testTextInput.hide();
  await tester.pumpAndSettle();
  final submit = find.byKey(const Key('account-editor-submit'));
  await tester.ensureVisible(submit);
  await tester.pumpAndSettle();
  await tester.tap(submit);
  await tester.pumpAndSettle();
}
