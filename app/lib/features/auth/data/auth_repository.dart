import 'dart:async';

import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/data/auth_remote_data_source.dart';
import 'package:fund_keeper/features/auth/data/auth_token_store.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

class AuthRepository {
  AuthRepository({
    required AuthTokenStore tokenStore,
    required AuthRemoteDataSource remote,
    DateTime Function()? now,
  }) : _tokenStore = tokenStore,
       _remote = remote,
       _now = now ?? DateTime.now;

  final AuthTokenStore _tokenStore;
  final AuthRemoteDataSource _remote;
  final DateTime Function() _now;
  final StreamController<AuthSession?> _sessionChanges =
      StreamController<AuthSession?>.broadcast(sync: true);

  AuthSession? _currentSession;
  Future<AuthSession>? _refreshing;

  Stream<AuthSession?> get sessionChanges => _sessionChanges.stream;

  AuthSession? get currentSession => _currentSession;

  Future<void> requestEmailCode({
    required String email,
    required EmailCodePurpose purpose,
  }) {
    return _remote.requestEmailCode(email: email.trim(), purpose: purpose);
  }

  Future<AuthUser> register({
    required String email,
    required String password,
    required String code,
  }) {
    return _remote.register(
      email: email.trim(),
      password: password,
      code: code,
    );
  }

  Future<void> resetPassword({
    required String email,
    required String code,
    required String newPassword,
  }) {
    return _remote.resetPassword(
      email: email.trim(),
      code: code,
      newPassword: newPassword,
    );
  }

  Future<AuthSession?> restoreSession() async {
    final stored = await _tokenStore.read();
    _currentSession = stored;
    if (stored == null) {
      return null;
    }
    if (stored.refreshTokenIsExpired(_now())) {
      await expireSession();
      return null;
    }

    try {
      final session = stored.accessTokenNeedsRefresh(_now())
          ? await refreshSession()
          : stored;
      final user = await _remote.currentUser(session.accessToken);
      final restored = session.copyWith(user: user);
      await _persist(restored);
      return restored;
    } on AuthenticationFailure {
      if (_currentSession == null) {
        return null;
      }
      try {
        return await refreshSession();
      } on AuthenticationFailure {
        return null;
      }
    }
  }

  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    final session = await _remote.login(email: email, password: password);
    await _persist(session);
    return session;
  }

  Future<AuthSession> refreshSession() {
    return _refreshing ??= _doRefresh().whenComplete(() {
      _refreshing = null;
    });
  }

  Future<AuthSession> _doRefresh() async {
    final session = _currentSession ?? await _tokenStore.read();
    if (session == null || session.refreshTokenIsExpired(_now())) {
      await expireSession();
      throw const AuthenticationFailure(message: '登录状态已失效，请重新登录');
    }

    try {
      final refreshed = await _remote.refresh(session.refreshToken);
      await _persist(refreshed);
      return refreshed;
    } on AuthenticationFailure {
      await expireSession();
      rethrow;
    }
  }

  Future<String?> accessToken() async {
    final session = _currentSession ?? await _tokenStore.read();
    _currentSession = session;
    return session?.accessToken;
  }

  Future<void> logout() async {
    final session = _currentSession ?? await _tokenStore.read();
    Object? failure;
    StackTrace? failureStackTrace;
    if (session != null) {
      try {
        await _remote.logout(session.refreshToken);
      } on Object catch (error, stackTrace) {
        failure = error;
        failureStackTrace = stackTrace;
      }
    }
    await expireSession();
    if (failure != null) {
      Error.throwWithStackTrace(failure, failureStackTrace!);
    }
  }

  Future<void> expireSession() async {
    _currentSession = null;
    await _tokenStore.clear();
    _sessionChanges.add(null);
  }

  Future<void> _persist(AuthSession session) async {
    await _tokenStore.write(session);
    _currentSession = session;
    _sessionChanges.add(session);
  }

  Future<void> dispose() => _sessionChanges.close();
}
