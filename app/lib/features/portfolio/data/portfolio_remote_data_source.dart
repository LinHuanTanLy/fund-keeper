import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_failure_mapper.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

abstract interface class PortfolioRemoteDataSource {
  Future<List<PortfolioAccount>> listAccounts();

  Future<PortfolioOverview> getOverview(String? accountId);

  Future<List<FundPortfolioCard>> listFunds(String? accountId);
}

class DioPortfolioRemoteDataSource implements PortfolioRemoteDataSource {
  DioPortfolioRemoteDataSource(this._dio);

  final Dio _dio;

  @override
  Future<List<PortfolioAccount>> listAccounts() async {
    final response = await _request(
      () => _dio.get<Object?>('/api/v1/accounts'),
    );
    return _requiredList(response)
        .map((item) => PortfolioAccount.fromJson(_requiredMap(item)))
        .where((account) => account.status == 'ACTIVE')
        .toList(growable: false);
  }

  @override
  Future<PortfolioOverview> getOverview(String? accountId) async {
    final response = await _request(
      () => _dio.get<Object?>(
        '/api/v1/portfolio/overview',
        queryParameters: _accountQuery(accountId),
      ),
    );
    return PortfolioOverview.fromJson(_requiredData(response));
  }

  @override
  Future<List<FundPortfolioCard>> listFunds(String? accountId) async {
    final response = await _request(
      () => _dio.get<Object?>(
        '/api/v1/portfolio/funds',
        queryParameters: _accountQuery(accountId),
      ),
    );
    return _requiredList(response)
        .map((item) => FundPortfolioCard.fromJson(_requiredMap(item)))
        .toList(growable: false);
  }

  Map<String, Object?>? _accountQuery(String? accountId) {
    return accountId == null ? null : {'accountId': accountId};
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
    final body = _requiredBody(response);
    return _requiredMap(body['data']);
  }

  List<Object?> _requiredList(Response<Object?> response) {
    final body = _requiredBody(response);
    final data = body['data'];
    if (data is! List) {
      throw const ProtocolFailure(message: '服务器响应缺少列表数据');
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
      throw const ProtocolFailure(message: '服务器响应缺少必要数据');
    }
    return value;
  }
}
