import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/auth/data/auth_remote_data_source.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

void main() {
  test('uses the backend registration and reset contracts exactly', () async {
    final adapter = _AuthContractAdapter();
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioAuthRemoteDataSource(dio);

    await remote.requestEmailCode(
      email: 'new@example.com',
      purpose: EmailCodePurpose.register,
    );
    final user = await remote.register(
      email: 'new@example.com',
      password: 'password-123',
      code: '123456',
    );
    await remote.requestEmailCode(
      email: 'new@example.com',
      purpose: EmailCodePurpose.resetPassword,
    );
    await remote.resetPassword(
      email: 'new@example.com',
      code: '654321',
      newPassword: 'new-password-123',
    );

    expect(user.email, 'new@example.com');
    expect(adapter.requests, hasLength(4));
    expect(adapter.requests[0].path, '/api/v1/auth/email-codes');
    expect(adapter.requests[0].data, {
      'email': 'new@example.com',
      'purpose': 'REGISTER',
    });
    expect(adapter.requests[1].path, '/api/v1/auth/register');
    expect(adapter.requests[1].data, {
      'email': 'new@example.com',
      'password': 'password-123',
      'code': '123456',
    });
    expect(adapter.requests[2].path, '/api/v1/auth/email-codes');
    expect(adapter.requests[2].data, {
      'email': 'new@example.com',
      'purpose': 'RESET_PASSWORD',
    });
    expect(adapter.requests[3].path, '/api/v1/auth/password-reset');
    expect(adapter.requests[3].data, {
      'email': 'new@example.com',
      'code': '654321',
      'newPassword': 'new-password-123',
    });

    dio.close(force: true);
  });
}

class _AuthContractAdapter implements HttpClientAdapter {
  final requests = <({String path, Map<String, Object?> data})>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add((
      path: options.path,
      data: Map<String, Object?>.from(options.data as Map),
    ));

    switch (options.path) {
      case '/api/v1/auth/email-codes':
        return _jsonResponse(202, {
          'code': 'OK',
          'message': '如果该邮箱可用于此操作，验证码邮件将会发送',
          'data': null,
        });
      case '/api/v1/auth/register':
        return _jsonResponse(201, {
          'code': 'OK',
          'message': 'success',
          'data': {'id': 'user-id', 'email': 'new@example.com'},
        });
      case '/api/v1/auth/password-reset':
        return ResponseBody.fromString('', 204);
      default:
        return _jsonResponse(404, {
          'code': 'NOT_FOUND',
          'message': 'not found',
          'data': null,
        });
    }
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
