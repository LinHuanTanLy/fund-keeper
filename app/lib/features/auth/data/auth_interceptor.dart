import 'package:dio/dio.dart';
import 'package:fund_keeper/features/auth/data/auth_repository.dart';

class AuthInterceptor extends Interceptor {
  AuthInterceptor({required Dio dio, required AuthRepository repository})
    : _dio = dio,
      _repository = repository;

  static const _retriedKey = 'fund_keeper.auth_retried';

  final Dio _dio;
  final AuthRepository _repository;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _repository.accessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final request = err.requestOptions;
    final currentToken = await _repository.accessToken();
    final shouldRefresh =
        err.response?.statusCode == 401 &&
        request.extra[_retriedKey] != true &&
        request.data is! FormData &&
        currentToken != null;

    if (!shouldRefresh) {
      handler.next(err);
      return;
    }

    try {
      final failedAuthorization = request.headers['Authorization'];
      final accessToken = failedAuthorization == 'Bearer $currentToken'
          ? (await _repository.refreshSession()).accessToken
          : currentToken;
      request.extra[_retriedKey] = true;
      request.headers['Authorization'] = 'Bearer $accessToken';
      final response = await _dio.fetch<Object?>(request);
      handler.resolve(response);
    } on Object {
      handler.next(err);
    }
  }
}
