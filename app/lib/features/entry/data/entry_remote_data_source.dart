import 'package:dio/dio.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_failure_mapper.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';

abstract interface class EntryRemoteDataSource {
  Future<ManualBuyResult> createManualBuy(ManualBuyDraft draft);

  Future<ManualSellResult> createManualSell(ManualSellDraft draft);

  Future<ImportPreflightResult> preflightImport(
    ImportKind kind,
    String rawJson,
  );

  Future<ImportCommitResult> commitImport(ImportKind kind, String batchId);
}

class DioEntryRemoteDataSource implements EntryRemoteDataSource {
  DioEntryRemoteDataSource(this._dio);

  final Dio _dio;

  @override
  Future<ManualBuyResult> createManualBuy(ManualBuyDraft draft) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/transactions/buys',
        data: draft.toJson(),
      );
      return ManualBuyResult.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      if (!_isTimeout(exception)) {
        throw mapDioException(exception);
      }
      try {
        final response = await _dio.get<Object?>(
          '/api/v1/transactions/requests/'
          '${Uri.encodeComponent(draft.requestId)}',
        );
        return ManualBuyResult.fromJson(_requiredData(response));
      } on DioException {
        throw mapDioException(exception);
      }
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<ManualSellResult> createManualSell(ManualSellDraft draft) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/transactions/sells',
        data: draft.toJson(),
      );
      return ManualSellResult.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      if (!_isTimeout(exception)) {
        throw mapDioException(exception);
      }
      try {
        final response = await _dio.get<Object?>(
          '/api/v1/transactions/requests/'
          '${Uri.encodeComponent(draft.requestId)}',
        );
        return ManualSellResult.fromJson(_requiredData(response));
      } on DioException {
        throw mapDioException(exception);
      }
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<ImportPreflightResult> preflightImport(
    ImportKind kind,
    String rawJson,
  ) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/imports/${kind.pathSegment}/preflight',
        data: rawJson,
        options: Options(contentType: Headers.jsonContentType),
      );
      return ImportPreflightResult.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
  }

  @override
  Future<ImportCommitResult> commitImport(
    ImportKind kind,
    String batchId,
  ) async {
    try {
      final response = await _dio.post<Object?>(
        '/api/v1/imports/${kind.pathSegment}/'
        '${Uri.encodeComponent(batchId)}/commit',
      );
      return ImportCommitResult.fromJson(_requiredData(response));
    } on DioException catch (exception) {
      throw mapDioException(exception);
    } on FormatException catch (exception) {
      throw ProtocolFailure(message: exception.message);
    }
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
