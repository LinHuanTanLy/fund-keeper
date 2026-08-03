import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/features/account/data/account_remote_data_source.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';

void main() {
  test('lists archived accounts and sends the exact create contract', () async {
    final adapter = _AccountContractAdapter();
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioAccountRemoteDataSource(dio);

    final accounts = await remote.listAccounts(includeArchived: true);
    final created = await remote.createAccount(
      const AccountDraft(name: '我的支付宝', platform: AccountPlatform.alipay),
    );

    expect(accounts.map((account) => account.status), ['ACTIVE', 'ARCHIVED']);
    expect(adapter.requests[0].queryParameters, {'includeArchived': true});
    expect(adapter.requests[1].method, 'POST');
    expect(adapter.requests[1].path, '/api/v1/accounts');
    expect(adapter.requests[1].data, {'name': '我的支付宝', 'platform': 'ALIPAY'});
    expect(created.platform, 'ALIPAY');
    dio.close(force: true);
  });

  test('update timeout succeeds only after matching account state', () async {
    final adapter = _AccountContractAdapter(
      timeoutPath: '/api/v1/accounts/account-1',
      recoveryName: '天天基金账户',
      recoveryPlatform: 'TIANTIAN_FUND',
    );
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioAccountRemoteDataSource(dio);

    final updated = await remote.updateAccount(
      'account-1',
      const AccountDraft(
        name: '天天基金账户',
        platform: AccountPlatform.tiantianFund,
      ),
    );

    expect(updated.name, '天天基金账户');
    expect(adapter.requests.map((request) => request.method), ['PUT', 'GET']);
    expect(adapter.requests.map((request) => request.path), [
      '/api/v1/accounts/account-1',
      '/api/v1/accounts/account-1',
    ]);
    expect(adapter.requests.first.data, {
      'name': '天天基金账户',
      'platform': 'TIANTIAN_FUND',
    });
    dio.close(force: true);
  });

  test('archive timeout is recovered only from archived state', () async {
    final adapter = _AccountContractAdapter(
      timeoutPath: '/api/v1/accounts/account-1/archive',
      recoveryStatus: 'ARCHIVED',
    );
    final dio = Dio(
      BaseOptions(
        baseUrl: 'https://api.example.test',
        responseType: ResponseType.json,
      ),
    )..httpClientAdapter = adapter;
    final remote = DioAccountRemoteDataSource(dio);

    final archived = await remote.archiveAccount('account-1');

    expect(archived.status, 'ARCHIVED');
    expect(adapter.requests.map((request) => request.path), [
      '/api/v1/accounts/account-1/archive',
      '/api/v1/accounts/account-1',
    ]);
    dio.close(force: true);
  });
}

class _AccountContractAdapter implements HttpClientAdapter {
  _AccountContractAdapter({
    this.timeoutPath,
    this.recoveryName = '默认账户',
    this.recoveryPlatform = 'OTHER',
    this.recoveryStatus = 'ACTIVE',
  });

  final String? timeoutPath;
  final String recoveryName;
  final String recoveryPlatform;
  final String recoveryStatus;
  final requests = <RequestOptions>[];
  bool _didTimeout = false;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (!_didTimeout && options.path == timeoutPath) {
      _didTimeout = true;
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
      );
    }
    if (options.path == '/api/v1/accounts' && options.method == 'GET') {
      return _jsonResponse([
        _accountJson(),
        _accountJson(id: 'account-archived', name: '历史账户', status: 'ARCHIVED'),
      ]);
    }
    if (options.path == '/api/v1/accounts' && options.method == 'POST') {
      return _jsonResponse(
        _accountJson(name: '我的支付宝', platform: 'ALIPAY'),
        statusCode: 201,
      );
    }
    return _jsonResponse(
      _accountJson(
        name: recoveryName,
        platform: recoveryPlatform,
        status: recoveryStatus,
      ),
    );
  }

  Map<String, Object?> _accountJson({
    String id = 'account-1',
    String name = '默认账户',
    String platform = 'OTHER',
    String status = 'ACTIVE',
  }) {
    return {
      'id': id,
      'name': name,
      'platform': platform,
      'status': status,
      'createdAt': '2026-07-28T08:00:00Z',
      'updatedAt': '2026-07-28T08:00:00Z',
      'archivedAt': status == 'ARCHIVED' ? '2026-07-28T09:00:00Z' : null,
    };
  }

  ResponseBody _jsonResponse(Object? data, {int statusCode = 200}) {
    return ResponseBody.fromString(
      jsonEncode({'code': 'OK', 'message': 'success', 'data': data}),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
