import 'package:flutter/material.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';

@immutable
class AppSemanticColors extends ThemeExtension<AppSemanticColors> {
  const AppSemanticColors({
    required this.profit,
    required this.loss,
    required this.warning,
    required this.gradientStart,
    required this.gradientEnd,
  });

  const AppSemanticColors.light()
    : profit = AppColors.profit,
      loss = AppColors.loss,
      warning = AppColors.warning,
      gradientStart = AppColors.brand,
      gradientEnd = AppColors.gradientEnd;

  final Color profit;
  final Color loss;
  final Color warning;
  final Color gradientStart;
  final Color gradientEnd;

  @override
  AppSemanticColors copyWith({
    Color? profit,
    Color? loss,
    Color? warning,
    Color? gradientStart,
    Color? gradientEnd,
  }) {
    return AppSemanticColors(
      profit: profit ?? this.profit,
      loss: loss ?? this.loss,
      warning: warning ?? this.warning,
      gradientStart: gradientStart ?? this.gradientStart,
      gradientEnd: gradientEnd ?? this.gradientEnd,
    );
  }

  @override
  AppSemanticColors lerp(covariant AppSemanticColors? other, double t) {
    if (other == null) {
      return this;
    }
    return AppSemanticColors(
      profit: Color.lerp(profit, other.profit, t) ?? profit,
      loss: Color.lerp(loss, other.loss, t) ?? loss,
      warning: Color.lerp(warning, other.warning, t) ?? warning,
      gradientStart:
          Color.lerp(gradientStart, other.gradientStart, t) ?? gradientStart,
      gradientEnd: Color.lerp(gradientEnd, other.gradientEnd, t) ?? gradientEnd,
    );
  }
}
