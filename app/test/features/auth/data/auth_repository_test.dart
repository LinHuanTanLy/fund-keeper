import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/data/auth_repository.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

import '../../../support/auth_fakes.dart';

void main() {
  group('AuthRepository', () {
    test('restores a stored session and validates the current user', () async {
      final store = MemoryAuthTokenStore(sampleSession());
      final remote = FakeAuthRemoteDataSource()
        ..currentUserResult = const AuthUser(
          id: 'user-id',
          email: 'latest@example.com',
        );
      final repository = AuthRepository(
        tokenStore: store,
        remote: remote,
        now: () => DateTime.utc(2029),
      );

      final restored = await repository.restoreSession();

      expect(restored?.user.email, 'latest@example.com');
      expect(remote.currentUserCalls, 1);
      expect(store.session?.user.email, 'latest@example.com');
      await repository.dispose();
    });

    test('coalesces concurrent refreshes into one rotating request', () async {
      final store = MemoryAuthTokenStore(sampleSession());
      final remote = FakeAuthRemoteDataSource();
      final refreshCompleter = Completer<AuthSession>();
      remote.onRefresh = (_) => refreshCompleter.future;
      final repository = AuthRepository(
        tokenStore: store,
        remote: remote,
        now: () => DateTime.utc(2029),
      );

      final first = repository.refreshSession();
      final second = repository.refreshSession();
      final third = repository.refreshSession();
      await Future<void>.delayed(Duration.zero);

      expect(remote.refreshCalls, 1);
      final refreshed = sampleSession(
        accessToken: 'new-access',
        refreshToken: 'new-refresh',
      );
      refreshCompleter.complete(refreshed);

      expect(await first, same(refreshed));
      expect(await second, same(refreshed));
      expect(await third, same(refreshed));
      expect(store.session, same(refreshed));
      await repository.dispose();
    });

    test('clears the local session when refresh is rejected', () async {
      final store = MemoryAuthTokenStore(sampleSession());
      final remote = FakeAuthRemoteDataSource()
        ..onRefresh = (_) => Future<AuthSession>.error(invalidRefreshToken());
      final repository = AuthRepository(
        tokenStore: store,
        remote: remote,
        now: () => DateTime.utc(2029),
      );

      await expectLater(
        repository.refreshSession(),
        throwsA(isA<AuthenticationFailure>()),
      );

      expect(store.session, isNull);
      expect(store.clearCount, 1);
      await repository.dispose();
    });

    test('local logout completes even when server revocation fails', () async {
      final store = MemoryAuthTokenStore(sampleSession());
      final remote = FakeAuthRemoteDataSource()
        ..logoutFailure = const NetworkFailure(message: 'offline');
      final repository = AuthRepository(
        tokenStore: store,
        remote: remote,
        now: () => DateTime.utc(2029),
      );

      await expectLater(repository.logout(), throwsA(isA<NetworkFailure>()));

      expect(store.session, isNull);
      expect(store.clearCount, 1);
      await repository.dispose();
    });
  });
}
