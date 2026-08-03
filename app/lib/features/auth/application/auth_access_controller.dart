import 'dart:async';

import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'auth_access_controller.g.dart';

class AuthAccessState {
  const AuthAccessState({
    this.isSendingCode = false,
    this.isSubmitting = false,
    this.resendAfterSeconds = 0,
    this.failure,
  });

  final bool isSendingCode;
  final bool isSubmitting;
  final int resendAfterSeconds;
  final AppFailure? failure;

  AuthAccessState copyWith({
    bool? isSendingCode,
    bool? isSubmitting,
    int? resendAfterSeconds,
    AppFailure? failure,
    bool clearFailure = false,
  }) {
    return AuthAccessState(
      isSendingCode: isSendingCode ?? this.isSendingCode,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      resendAfterSeconds: resendAfterSeconds ?? this.resendAfterSeconds,
      failure: clearFailure ? null : failure ?? this.failure,
    );
  }
}

@riverpod
class AuthAccessController extends _$AuthAccessController {
  static const _serverCooldownSeconds = 60;

  Timer? _cooldownTimer;
  late EmailCodePurpose _purpose;

  @override
  AuthAccessState build(EmailCodePurpose purpose) {
    _purpose = purpose;
    ref.onDispose(() => _cooldownTimer?.cancel());
    return const AuthAccessState();
  }

  Future<bool> sendCode(String email) async {
    if (state.isSendingCode || state.resendAfterSeconds > 0) {
      return false;
    }

    state = state.copyWith(isSendingCode: true, clearFailure: true);
    try {
      await ref
          .read(authRepositoryProvider)
          .requestEmailCode(email: email, purpose: _purpose);
      state = state.copyWith(
        isSendingCode: false,
        resendAfterSeconds: _serverCooldownSeconds,
        clearFailure: true,
      );
      _startCooldown();
      return true;
    } on AppFailure catch (failure) {
      state = state.copyWith(isSendingCode: false, failure: failure);
      if (failure.code == 'EMAIL_CODE_RATE_LIMITED') {
        state = state.copyWith(resendAfterSeconds: _serverCooldownSeconds);
        _startCooldown();
      }
      return false;
    } on Object {
      state = state.copyWith(
        isSendingCode: false,
        failure: const UnknownFailure(message: '验证码发送失败，请稍后重试'),
      );
      return false;
    }
  }

  Future<bool> submit({
    required String email,
    required String code,
    required String password,
  }) async {
    if (state.isSubmitting) {
      return false;
    }

    state = state.copyWith(isSubmitting: true, clearFailure: true);
    try {
      final repository = ref.read(authRepositoryProvider);
      switch (_purpose) {
        case EmailCodePurpose.register:
          await repository.register(
            email: email,
            password: password,
            code: code,
          );
          break;
        case EmailCodePurpose.resetPassword:
          await repository.resetPassword(
            email: email,
            code: code,
            newPassword: password,
          );
          break;
      }
      state = state.copyWith(isSubmitting: false, clearFailure: true);
      return true;
    } on AppFailure catch (failure) {
      state = state.copyWith(isSubmitting: false, failure: failure);
      return false;
    } on Object {
      state = state.copyWith(
        isSubmitting: false,
        failure: const UnknownFailure(message: '提交失败，请稍后重试'),
      );
      return false;
    }
  }

  void _startCooldown() {
    _cooldownTimer?.cancel();
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final remaining = state.resendAfterSeconds;
      if (remaining <= 1) {
        timer.cancel();
        state = state.copyWith(resendAfterSeconds: 0);
        return;
      }
      state = state.copyWith(resendAfterSeconds: remaining - 1);
    });
  }
}
