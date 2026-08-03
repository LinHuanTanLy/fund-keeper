import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/auth/data/auth_interceptor.dart';
import 'package:fund_keeper/features/auth/data/auth_repository.dart';

import '../../../support/auth_fakes.dart';

void main() {
  test(
    'concurrent 401 responses refresh once and replay with the new token',
    () async {
      final store = MemoryAuthTokenStore(sampleSession());
      final remote = FakeAuthRemoteDataSource();
      final repository = AuthRepository(
        tokenStore: store,
        remote: remote,
        now: () => DateTime.utc(2029),
      );
      await repository.accessToken();

      final adapter = _ProtectedEndpointAdapter();
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.example.test',
          responseType: ResponseType.json,
        ),
      )..httpClientAdapter = adapter;
      dio.interceptors.add(AuthInterceptor(dio: dio, repository: repository));

      final responses = await Future.wait([
        dio.get<Object?>('/protected/one'),
        dio.get<Object?>('/protected/two'),
      ]);

      expect(
        responses.map((response) => response.statusCode),
        everyElement(200),
      );
      expect(remote.refreshCalls, 1);
      expect(adapter.oldTokenRequests, 2);
      expect(adapter.newTokenRequests, 2);

      dio.close(force: true);
      await repository.dispose();
    },
  );
}

class _ProtectedEndpointAdapter implements HttpClientAdapter {
  int oldTokenRequests = 0;
  int newTokenRequests = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final authorization = options.headers['Authorization'];
    if (authorization == 'Bearer refreshed-access-token') {
      newTokenRequests += 1;
      return _jsonResponse(200, {
        'code': 'OK',
        'message': 'success',
        'data': {'accepted': true},
      });
    }

    oldTokenRequests += 1;
    return _jsonResponse(401, {
      'code': 'AUTHENTICATION_REQUIRED',
      'message': '请先登录或重新登录',
      'data': null,
    });
  }

  ResponseBody _jsonResponse(int statusCode, Map<String, Object?> body) {
    return ResponseBody.fromString(
      jsonEncode(body),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
