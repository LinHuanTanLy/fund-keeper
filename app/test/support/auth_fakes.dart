import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/data/auth_remote_data_source.dart';
import 'package:fund_keeper/features/auth/data/auth_token_store.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

AuthSession sampleSession({
  String accessToken = 'access-token',
  String refreshToken = 'refresh-token',
  DateTime? accessTokenExpiresAt,
  DateTime? refreshTokenExpiresAt,
  String email = 'user@example.com',
}) {
  return AuthSession(
    accessToken: accessToken,
    refreshToken: refreshToken,
    tokenType: 'Bearer',
    accessTokenExpiresAt: accessTokenExpiresAt ?? DateTime.utc(2030, 1, 1),
    refreshTokenExpiresAt: refreshTokenExpiresAt ?? DateTime.utc(2030, 2, 1),
    user: AuthUser(id: 'user-id', email: email),
  );
}

class MemoryAuthTokenStore implements AuthTokenStore {
  MemoryAuthTokenStore([this.session]);

  AuthSession? session;
  int writeCount = 0;
  int clearCount = 0;

  @override
  Future<void> clear() async {
    clearCount += 1;
    session = null;
  }

  @override
  Future<AuthSession?> read() async => session;

  @override
  Future<void> write(AuthSession session) async {
    writeCount += 1;
    this.session = session;
  }
}

class FakeAuthRemoteDataSource implements AuthRemoteDataSource {
  AuthSession loginResult = sampleSession();
  AuthSession refreshResult = sampleSession(
    accessToken: 'refreshed-access-token',
    refreshToken: 'rotated-refresh-token',
  );
  AuthUser currentUserResult = const AuthUser(
    id: 'user-id',
    email: 'user@example.com',
  );
  Future<AuthSession> Function(String refreshToken)? onRefresh;
  Object? requestCodeFailure;
  Object? registerFailure;
  Object? loginFailure;
  Object? currentUserFailure;
  Object? logoutFailure;
  Object? resetPasswordFailure;

  int requestCodeCalls = 0;
  int registerCalls = 0;
  int loginCalls = 0;
  int refreshCalls = 0;
  int currentUserCalls = 0;
  int logoutCalls = 0;
  int resetPasswordCalls = 0;
  String? lastEmail;
  String? lastCode;
  String? lastPassword;
  EmailCodePurpose? lastCodePurpose;

  @override
  Future<void> requestEmailCode({
    required String email,
    required EmailCodePurpose purpose,
  }) async {
    requestCodeCalls += 1;
    lastEmail = email;
    lastCodePurpose = purpose;
    if (requestCodeFailure case final Object failure) {
      throw failure;
    }
  }

  @override
  Future<AuthUser> register({
    required String email,
    required String password,
    required String code,
  }) async {
    registerCalls += 1;
    lastEmail = email;
    lastPassword = password;
    lastCode = code;
    if (registerFailure case final Object failure) {
      throw failure;
    }
    return AuthUser(id: 'user-id', email: email);
  }

  @override
  Future<AuthUser> currentUser(String accessToken) async {
    currentUserCalls += 1;
    if (currentUserFailure case final Object failure) {
      throw failure;
    }
    return currentUserResult;
  }

  @override
  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    loginCalls += 1;
    if (loginFailure case final Object failure) {
      throw failure;
    }
    return loginResult;
  }

  @override
  Future<void> logout(String refreshToken) async {
    logoutCalls += 1;
    if (logoutFailure case final Object failure) {
      throw failure;
    }
  }

  @override
  Future<AuthSession> refresh(String refreshToken) async {
    refreshCalls += 1;
    final handler = onRefresh;
    if (handler != null) {
      return handler(refreshToken);
    }
    return refreshResult;
  }

  @override
  Future<void> resetPassword({
    required String email,
    required String code,
    required String newPassword,
  }) async {
    resetPasswordCalls += 1;
    lastEmail = email;
    lastCode = code;
    lastPassword = newPassword;
    if (resetPasswordFailure case final Object failure) {
      throw failure;
    }
  }
}

AuthenticationFailure invalidRefreshToken() {
  return const AuthenticationFailure(
    message: '登录状态已失效，请重新登录',
    code: 'INVALID_REFRESH_TOKEN',
  );
}
