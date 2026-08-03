import 'package:dio/dio.dart';
import 'package:fund_keeper/core/config/app_environment.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/auth/data/auth_interceptor.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'dio_providers.g.dart';

@Riverpod(keepAlive: true)
Dio authDio(Ref ref) {
  return _createDio(ref, ref.watch(appEnvironmentProvider));
}

@Riverpod(keepAlive: true)
Dio apiDio(Ref ref) {
  final dio = _createDio(ref, ref.watch(appEnvironmentProvider));
  dio.interceptors.add(
    AuthInterceptor(dio: dio, repository: ref.watch(authRepositoryProvider)),
  );
  return dio;
}

Dio _createDio(Ref ref, AppEnvironment environment) {
  final dio = Dio(
    BaseOptions(
      baseUrl: environment.apiBaseUrl.toString(),
      connectTimeout: environment.requestTimeout,
      receiveTimeout: environment.requestTimeout,
      sendTimeout: environment.requestTimeout,
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    ),
  );
  ref.onDispose(dio.close);
  return dio;
}
