import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';

AppFailure mapDioException(DioException exception) {
  final response = exception.response;
  final body = response?.data;
  final json = body is Map<String, dynamic> ? body : null;
  final code = json?['code'] as String?;
  final message = json?['message'] as String?;
  final statusCode = response?.statusCode;

  if (statusCode == 401) {
    return AuthenticationFailure(
      message: message ?? '登录状态已失效，请重新登录',
      code: code,
    );
  }
  if (statusCode == 403) {
    return PermissionFailure(message: message ?? '无权执行此操作', code: code);
  }
  if (statusCode == 400) {
    final rawFields = json?['data'];
    final fields = rawFields is Map
        ? rawFields.map(
            (key, value) => MapEntry(key.toString(), value.toString()),
          )
        : const <String, String>{};
    return ValidationFailure(
      message: message ?? '请求参数不合法',
      fieldErrors: fields,
      code: code,
    );
  }
  if (statusCode == 503) {
    return ServiceUnavailableFailure(message: message ?? '服务暂时不可用', code: code);
  }
  if (statusCode != null) {
    return BusinessFailure(
      message: message ?? '请求失败，请稍后重试',
      code: code ?? 'HTTP_$statusCode',
    );
  }

  switch (exception.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
    case DioExceptionType.transformTimeout:
      return const NetworkFailure(message: '请求超时，请检查网络后重试');
    case DioExceptionType.connectionError:
      return const NetworkFailure(message: '无法连接服务器，请检查网络');
    case DioExceptionType.cancel:
      return const NetworkFailure(message: '请求已取消');
    case DioExceptionType.badCertificate:
      return const NetworkFailure(message: '服务器证书校验失败');
    case DioExceptionType.badResponse:
    case DioExceptionType.unknown:
      return const NetworkFailure(message: '网络请求失败，请稍后重试');
  }
}
