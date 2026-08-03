import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_failure_mapper.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';

abstract interface class TransactionRemoteDataSource {
  Future<TransactionPage> listTransactions({
    required TransactionFilters filters,
    required int page,
    required int size,
  });

  Future<TransactionRecord> confirmBuy(
    String transactionId,
    BuyConfirmationDraft draft,
  );

  Future<TransactionRecord> confirmSell(
    String transactionId,
    SellConfirmationDraft draft,
  );

  Future<TransactionRecord> cancel(String transactionId, String? reason);
}

class DioTransactionRemoteDataSource implements TransactionRemoteDataSource {
  DioTransactionRemoteDataSource(this._dio);

  final Dio _dio;

  @override
  Future<TransactionPage> listTransactions({
    required TransactionFilters filters,
    required int page,
    required int size,
  }) async {
    try {
      final response = await _dio.get<Object?>(
        '/api/v1/transactions',
        queryParameters: {
          if (filters.accountId != null) 'accountId': filters.accountId,
          if (filters.fundCode != null) 'fundCode': filters.fundCode,
          if (filters.type != null) 'type': filters.type,
          if (filters.status != null) 'status': filters.status,
          'page': page,
          'size': size,
        },
      );
      return TransactionPage.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message.toString());
    }
  }

  @override
  Future<TransactionRecord> confirmBuy(
    String transactionId,
    BuyConfirmationDraft draft,
  ) {
    return _mutateWithStatusRecovery(
      transactionId: transactionId,
      desiredStatus: 'CONFIRMED',
      request: () => _dio.post<Object?>(
        '/api/v1/transactions/'
        '${Uri.encodeComponent(transactionId)}/buy-confirmation',
        data: draft.toJson(),
      ),
    );
  }

  @override
  Future<TransactionRecord> confirmSell(
    String transactionId,
    SellConfirmationDraft draft,
  ) {
    return _mutateWithStatusRecovery(
      transactionId: transactionId,
      desiredStatus: 'CONFIRMED',
      request: () => _dio.post<Object?>(
        '/api/v1/transactions/'
        '${Uri.encodeComponent(transactionId)}/sell-confirmation',
        data: draft.toJson(),
      ),
    );
  }

  @override
  Future<TransactionRecord> cancel(String transactionId, String? reason) {
    return _mutateWithStatusRecovery(
      transactionId: transactionId,
      desiredStatus: 'CANCELLED',
      request: () => _dio.post<Object?>(
        '/api/v1/transactions/'
        '${Uri.encodeComponent(transactionId)}/cancel',
        data: {
          'reason': reason?.trim().isEmpty == true ? null : reason?.trim(),
        },
      ),
    );
  }

  Future<TransactionRecord> _mutateWithStatusRecovery({
    required String transactionId,
    required String desiredStatus,
    required Future<Response<Object?>> Function() request,
  }) async {
    try {
      final response = await request();
      return TransactionRecord.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      if (!_isTimeout(exception)) {
        throw mapDioException(exception);
      }
      try {
        final recovered = await _getTransaction(transactionId);
        if (recovered.status == desiredStatus) {
          return recovered;
        }
      } on Object {
        // Preserve the original timeout when final state cannot be proven.
      }
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message.toString());
    }
  }

  Future<TransactionRecord> _getTransaction(String transactionId) async {
    final response = await _dio.get<Object?>(
      '/api/v1/transactions/${Uri.encodeComponent(transactionId)}',
    );
    return TransactionRecord.fromJson(_requiredData(response));
  }

  Map<String, dynamic> _requiredData(Response<Object?> response) {
    final body = response.data;
    if (body is! Map<String, dynamic> || body['code'] != 'OK') {
      throw const ProtocolFailure(message: '服务器返回了无法识别的数据');
    }
    final data = body['data'];
    if (data is! Map<String, dynamic>) {
      throw const ProtocolFailure(message: '服务器响应缺少必要数据');
    }
    return data;
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
