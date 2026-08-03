import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';

abstract final class PortfolioFormatters {
  static String money(Decimal? value, {bool signed = false}) {
    if (value == null) {
      return '--';
    }
    final negative = value < Decimal.zero;
    final absolute = negative ? -value : value;
    final parts = absolute.toStringAsFixed(2).split('.');
    final integer = parts.first.replaceAllMapped(
      RegExp(r'\B(?=(\d{3})+(?!\d))'),
      (_) => ',',
    );
    final sign = negative
        ? '-'
        : signed && value > Decimal.zero
        ? '+'
        : '';
    return '$sign¥$integer.${parts.last}';
  }

  static String percent(Decimal? value, {bool signed = false}) {
    if (value == null) {
      return '--';
    }
    final sign = value < Decimal.zero
        ? ''
        : signed && value > Decimal.zero
        ? '+'
        : '';
    return '$sign${value.toStringAsFixed(2)}%';
  }

  static Color valueColor(Decimal? value) {
    if (value == null || value == Decimal.zero) {
      return AppColors.secondaryText;
    }
    return value > Decimal.zero ? AppColors.profit : AppColors.loss;
  }

  static String dateTime(DateTime? value) {
    if (value == null) {
      return '--';
    }
    final local = value.toLocal();
    String two(int number) => number.toString().padLeft(2, '0');
    return '${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}';
  }

  static String date(DateTime? value) {
    if (value == null) {
      return '--';
    }
    final local = value.toLocal();
    String two(int number) => number.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)}';
  }
}
