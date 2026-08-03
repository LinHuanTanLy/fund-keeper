import 'package:decimal/decimal.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fund_keeper/core/network/decimal_json_converter.dart';

void main() {
  const converter = DecimalJsonConverter();

  test('reads both JSON numbers and numeric strings', () {
    expect(converter.fromJson(123.45), Decimal.parse('123.45'));
    expect(converter.fromJson('9876.5432'), Decimal.parse('9876.5432'));
  });

  test('writes a JSON number at the transport edge', () {
    expect(converter.toJson(Decimal.parse('123.4500')), isA<num>());
  });

  test('rejects unsupported JSON values', () {
    expect(() => converter.fromJson(true), throwsFormatException);
  });
}
