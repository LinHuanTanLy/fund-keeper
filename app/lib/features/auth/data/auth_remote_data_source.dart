import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_failure_mapper.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

abstract interface class AuthRemoteDataSource {
  Future<void> requestEmailCode({
    required String email,
    required EmailCodePurpose purpose,
  });

  Future<AuthUser> register({
    required String email,
    required String password,
    required String code,
  });

  Future<AuthSession> login({required String email, required String password});

  Future<AuthSession> refresh(String refreshToken);

  Future<AuthUser> currentUser(String accessToken);

  Future<void> logout(String refreshToken);

  Future<void> resetPassword({
    required String email,
    required String code,
    required String newPassword,
  });
}

class DioAuthRemoteDataSource implements AuthRemoteDataSource {
  DioAuthRemoteDataSource(this._dio);

  final Dio _dio;

  @override
  Future<void> requestEmailCode({
    required String email,
    required EmailCodePurpose purpose,
  }) async {
    final response = await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/email-codes',
        data: {'email': email, 'purpose': purpose.wireValue},
      ),
    );
    _requireSuccess(response);
  }

  @override
  Future<AuthUser> register({
    required String email,
    required String password,
    required String code,
  }) async {
    final response = await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/register',
        data: {'email': email, 'password': password, 'code': code},
      ),
    );
    return AuthUser.fromJson(_requiredData(response));
  }

  @override
  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    final response = await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/login',
        data: {'email': email, 'password': password},
      ),
    );
    return AuthSession.fromJson(_requiredData(response));
  }

  @override
  Future<AuthSession> refresh(String refreshToken) async {
    final response = await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      ),
    );
    return AuthSession.fromJson(_requiredData(response));
  }

  @override
  Future<AuthUser> currentUser(String accessToken) async {
    final response = await _request(
      () => _dio.get<Object?>(
        '/api/v1/auth/me',
        options: Options(headers: {'Authorization': 'Bearer $accessToken'}),
      ),
    );
    return AuthUser.fromJson(_requiredData(response));
  }

  @override
  Future<void> logout(String refreshToken) async {
    await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/logout',
        data: {'refreshToken': refreshToken},
      ),
    );
  }

  @override
  Future<void> resetPassword({
    required String email,
    required String code,
    required String newPassword,
  }) async {
    await _request(
      () => _dio.post<Object?>(
        '/api/v1/auth/password-reset',
        data: {'email': email, 'code': code, 'newPassword': newPassword},
      ),
    );
  }

  Future<Response<Object?>> _request(
    Future<Response<Object?>> Function() request,
  ) async {
    try {
      return await request();
    } on DioException catch (exception) {
      throw mapDioException(exception);
    }
  }

  Map<String, dynamic> _requiredData(Response<Object?> response) {
    _requireSuccess(response);
    final body = response.data! as Map<String, dynamic>;
    final data = body['data'];
    if (data is! Map<String, dynamic>) {
      throw const ProtocolFailure(message: '服务器响应缺少必要数据');
    }
    return data;
  }

  void _requireSuccess(Response<Object?> response) {
    final body = response.data;
    if (body is! Map<String, dynamic> || body['code'] != 'OK') {
      throw const ProtocolFailure(message: '服务器返回了无法识别的数据');
    }
  }
}
