import 'dart:async';

import 'package:fund_keeper/core/network/dio_providers.dart';
import 'package:fund_keeper/features/auth/data/auth_remote_data_source.dart';
import 'package:fund_keeper/features/auth/data/auth_repository.dart';
import 'package:fund_keeper/features/auth/data/auth_token_store.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'auth_providers.g.dart';

@Riverpod(keepAlive: true)
AuthTokenStore authTokenStore(Ref ref) {
  return SecureAuthTokenStore();
}

@Riverpod(keepAlive: true)
AuthRemoteDataSource authRemoteDataSource(Ref ref) {
  return DioAuthRemoteDataSource(ref.watch(authDioProvider));
}

@Riverpod(keepAlive: true)
AuthRepository authRepository(Ref ref) {
  final repository = AuthRepository(
    tokenStore: ref.watch(authTokenStoreProvider),
    remote: ref.watch(authRemoteDataSourceProvider),
  );
  ref.onDispose(() => unawaited(repository.dispose()));
  return repository;
}

@Riverpod(keepAlive: true)
class AuthSessionController extends _$AuthSessionController {
  StreamSubscription<AuthSession?>? _subscription;

  @override
  Future<AuthSession?> build() async {
    final repository = ref.watch(authRepositoryProvider);
    final initialSession = await repository.restoreSession();
    _subscription = repository.sessionChanges.listen((session) {
      state = AsyncData(session);
    });
    ref.onDispose(() => unawaited(_subscription?.cancel()));
    return initialSession;
  }

  Future<void> retryRestore() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      ref.read(authRepositoryProvider).restoreSession,
    );
  }

  Future<void> logout() async {
    try {
      await ref.read(authRepositoryProvider).logout();
    } finally {
      state = const AsyncData(null);
    }
  }
}

@riverpod
class LoginController extends _$LoginController {
  @override
  FutureOr<void> build() {}

  Future<void> login({required String email, required String password}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => ref
          .read(authRepositoryProvider)
          .login(email: email.trim(), password: password),
    );
  }
}
