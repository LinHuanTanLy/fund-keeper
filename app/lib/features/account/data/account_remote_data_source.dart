import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_failure_mapper.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

abstract interface class AccountRemoteDataSource {
  Future<List<PortfolioAccount>> listAccounts({required bool includeArchived});

  Future<PortfolioAccount> createAccount(AccountDraft draft);

  Future<PortfolioAccount> updateAccount(String accountId, AccountDraft draft);

  Future<PortfolioAccount> archiveAccount(String accountId);
}

class DioAccountRemoteDataSource implements AccountRemoteDataSource {
  DioAccountRemoteDataSource(this._dio);

  final Dio _dio;

  @override
  Future<List<PortfolioAccount>> listAccounts({
    required bool includeArchived,
  }) async {
    try {
      final response = await _dio.get<Object?>(
        '/api/v1/accounts',
        queryParameters: {'includeArchived': includeArchived},
      );
      return _requiredList(response)
          .map((item) => PortfolioAccount.fromJson(_requiredMap(item)))
          .toList(growable: false);
    } on DioException catch (exception) {
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<PortfolioAccount> createAccount(AccountDraft draft) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/accounts',
        data: draft.toJson(),
      );
      return PortfolioAccount.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<PortfolioAccount> updateAccount(
    String accountId,
    AccountDraft draft,
  ) async {
    try {
      final response = await _dio.put<Object?>(
        '/api/v1/accounts/${Uri.encodeComponent(accountId)}',
        data: draft.toJson(),
      );
      return PortfolioAccount.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      if (!_isTimeout(exception)) {
        throw mapDioException(exception);
      }
      final recovered = await _recover(
        accountId,
        (account) =>
            account.status == 'ACTIVE' &&
            account.name == draft.name.trim() &&
            account.platform == draft.platform.apiValue,
      );
      if (recovered != null) {
        return recovered;
      }
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<PortfolioAccount> archiveAccount(String accountId) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/accounts/${Uri.encodeComponent(accountId)}/archive',
      );
      return PortfolioAccount.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      if (!_isTimeout(exception)) {
        throw mapDioException(exception);
      }
      final recovered = await _recover(
        accountId,
        (account) => account.status == 'ARCHIVED',
      );
      if (recovered != null) {
        return recovered;
      }
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  Future<PortfolioAccount?> _recover(
    String accountId,
    bool Function(PortfolioAccount account) matches,
  ) async {
    try {
      final response = await _dio.get<Object?>(
        '/api/v1/accounts/${Uri.encodeComponent(accountId)}',
      );
      final account = PortfolioAccount.fromJson(_requiredData(response));
      return matches(account) ? account : null;
    } on Object {
      return null;
    }
  }

  Map<String, dynamic> _requiredData(Response<Object?> response) {
    final body = _requiredBody(response);
    return _requiredMap(body['data']);
  }

  List<Object?> _requiredList(Response<Object?> response) {
    final body = _requiredBody(response);
    final data = body['data'];
    if (data is! List) {
      throw const ProtocolFailure(message: '服务器响应缺少账户列表');
    }
    return data.cast<Object?>();
  }

  Map<String, dynamic> _requiredBody(Response<Object?> response) {
    final body = response.data;
    if (body is! Map<String, dynamic> || body['code'] != 'OK') {
      throw const ProtocolFailure(message: '服务器返回了无法识别的数据');
    }
    return body;
  }

  Map<String, dynamic> _requiredMap(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const ProtocolFailure(message: '服务器响应缺少必要账户数据');
    }
    return value;
  }

  bool _isTimeout(DioException exception) {
    return switch (exception.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout ||
      DioExceptionType.transformTimeout => true,
      _ => false,
    };
  }
}
