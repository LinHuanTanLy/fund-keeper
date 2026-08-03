import 'package:json_annotation/json_annotation.dart';

part 'api_response.g.dart';

@JsonSerializable(genericArgumentFactories: true)
class ApiResponse<T> {
  const ApiResponse({
    required this.code,
    required this.message,
    required this.data,
    required this.timestamp,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? json) fromJsonT,
  ) {
    return _$ApiResponseFromJson(json, fromJsonT);
  }

  final String code;
  final String message;
  final T? data;
  final DateTime timestamp;

  Map<String, dynamic> toJson(Object? Function(T value) toJsonT) {
    return _$ApiResponseToJson(this, toJsonT);
  }
}
