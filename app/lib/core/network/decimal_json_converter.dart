import 'package:decimal/decimal.dart';
import 'package:json_annotation/json_annotation.dart';

class DecimalJsonConverter implements JsonConverter<Decimal, Object> {
  const DecimalJsonConverter();

  @override
  Decimal fromJson(Object json) {
    if (json is! num && json is! String) {
      throw FormatException(
        'Expected a JSON number or numeric string, got ${json.runtimeType}.',
      );
    }

    return Decimal.parse(json.toString());
  }

  @override
  Object toJson(Decimal object) {
    return num.parse(object.toString());
  }
}

class NullableDecimalJsonConverter implements JsonConverter<Decimal?, Object?> {
  const NullableDecimalJsonConverter();

  @override
  Decimal? fromJson(Object? json) {
    if (json == null) {
      return null;
    }
    return const DecimalJsonConverter().fromJson(json);
  }

  @override
  Object? toJson(Decimal? object) {
    if (object == null) {
      return null;
    }
    return const DecimalJsonConverter().toJson(object);
  }
}
