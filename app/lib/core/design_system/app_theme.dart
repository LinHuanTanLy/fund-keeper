import 'package:flutter/material.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_semantic_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';

abstract final class AppTheme {
  static ThemeData light() {
    final colorScheme =
        ColorScheme.fromSeed(
          seedColor: AppColors.brand,
          brightness: Brightness.light,
        ).copyWith(
          primary: AppColors.brandDark,
          onPrimary: Colors.white,
          surface: AppColors.cardBackground,
          onSurface: AppColors.primaryText,
          error: AppColors.profit,
          outline: AppColors.border,
        );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.pageBackground,
      textTheme: const TextTheme(
        headlineMedium: TextStyle(
          color: AppColors.primaryText,
          fontWeight: FontWeight.w700,
        ),
        titleLarge: TextStyle(
          color: AppColors.primaryText,
          fontWeight: FontWeight.w700,
        ),
        titleMedium: TextStyle(
          color: AppColors.primaryText,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(color: AppColors.primaryText),
        bodyMedium: TextStyle(color: AppColors.secondaryText),
      ),
      cardTheme: const CardThemeData(
        color: AppColors.cardBackground,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(AppRadius.lg)),
          side: BorderSide(color: AppColors.border),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.cardBackground,
        indicatorColor: AppColors.brand.withValues(alpha: 0.16),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final isSelected = states.contains(WidgetState.selected);
          return TextStyle(
            color: isSelected ? AppColors.brandDark : AppColors.secondaryText,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
          );
        }),
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: AppColors.brand,
        foregroundColor: Colors.white,
        shape: CircleBorder(),
      ),
      snackBarTheme: const SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        filled: true,
        fillColor: AppColors.cardBackground,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(AppRadius.md)),
          borderSide: BorderSide(color: AppColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(AppRadius.md)),
          borderSide: BorderSide(color: AppColors.border),
        ),
      ),
      extensions: const [AppSemanticColors.light()],
    );
  }
}
