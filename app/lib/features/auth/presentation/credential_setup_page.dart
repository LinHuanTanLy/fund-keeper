import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/features/auth/application/auth_access_controller.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

class CredentialSetupPage extends ConsumerStatefulWidget {
  const CredentialSetupPage({required this.purpose, super.key});

  final EmailCodePurpose purpose;

  @override
  ConsumerState<CredentialSetupPage> createState() =>
      _CredentialSetupPageState();
}

class _CredentialSetupPageState extends ConsumerState<CredentialSetupPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailFieldKey = GlobalKey<FormFieldState<String>>();
  final _emailController = TextEditingController();
  final _codeController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _obscurePassword = true;
  bool _obscureConfirmation = true;

  bool get _isRegistration => widget.purpose == EmailCodePurpose.register;

  @override
  void dispose() {
    _emailController.dispose();
    _codeController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final provider = authAccessControllerProvider(widget.purpose);
    final accessState = ref.watch(provider);
    final isBusy = accessState.isSendingCode || accessState.isSubmitting;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          _isRegistration ? strings.createAccount : strings.resetPassword,
        ),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      _isRegistration
                          ? strings.registerDescription
                          : strings.resetPasswordDescription,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    TextFormField(
                      key: _emailFieldKey,
                      controller: _emailController,
                      enabled: !isBusy && accessState.resendAfterSeconds == 0,
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.email],
                      autocorrect: false,
                      decoration: InputDecoration(
                        labelText: strings.email,
                        prefixIcon: const Icon(Icons.mail_outline_rounded),
                      ),
                      validator: (value) => _validateEmail(value, strings),
                    ),
                    const SizedBox(height: AppSpacing.md),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _codeController,
                            enabled: !accessState.isSubmitting,
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.next,
                            autofillHints: const [AutofillHints.oneTimeCode],
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                              LengthLimitingTextInputFormatter(6),
                            ],
                            decoration: InputDecoration(
                              labelText: strings.verificationCode,
                              prefixIcon: const Icon(Icons.verified_outlined),
                              counterText: '',
                            ),
                            maxLength: 6,
                            validator: (value) {
                              if (!RegExp(r'^\d{6}$').hasMatch(value ?? '')) {
                                return strings.verificationCodeInvalid;
                              }
                              return null;
                            },
                          ),
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        SizedBox(
                          height: 56,
                          child: OutlinedButton(
                            onPressed:
                                accessState.isSendingCode ||
                                    accessState.resendAfterSeconds > 0
                                ? null
                                : _sendCode,
                            child: accessState.isSendingCode
                                ? const SizedBox.square(
                                    dimension: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : Text(
                                    accessState.resendAfterSeconds > 0
                                        ? strings.resendInSeconds(
                                            accessState.resendAfterSeconds,
                                          )
                                        : strings.sendCode,
                                  ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.md),
                    TextFormField(
                      controller: _passwordController,
                      enabled: !isBusy,
                      obscureText: _obscurePassword,
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.newPassword],
                      decoration: InputDecoration(
                        labelText: _isRegistration
                            ? strings.password
                            : strings.newPassword,
                        prefixIcon: const Icon(Icons.lock_outline_rounded),
                        suffixIcon: IconButton(
                          onPressed: () {
                            setState(() {
                              _obscurePassword = !_obscurePassword;
                            });
                          },
                          icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_outlined
                                : Icons.visibility_off_outlined,
                          ),
                        ),
                      ),
                      validator: (value) => _validatePassword(value, strings),
                    ),
                    const SizedBox(height: AppSpacing.md),
                    TextFormField(
                      controller: _confirmPasswordController,
                      enabled: !isBusy,
                      obscureText: _obscureConfirmation,
                      textInputAction: TextInputAction.done,
                      autofillHints: const [AutofillHints.newPassword],
                      onFieldSubmitted: (_) => _submit(),
                      decoration: InputDecoration(
                        labelText: strings.confirmPassword,
                        prefixIcon: const Icon(Icons.lock_reset_rounded),
                        suffixIcon: IconButton(
                          onPressed: () {
                            setState(() {
                              _obscureConfirmation = !_obscureConfirmation;
                            });
                          },
                          icon: Icon(
                            _obscureConfirmation
                                ? Icons.visibility_outlined
                                : Icons.visibility_off_outlined,
                          ),
                        ),
                      ),
                      validator: (value) {
                        if (value != _passwordController.text) {
                          return strings.passwordsDoNotMatch;
                        }
                        return null;
                      },
                    ),
                    if (accessState.failure != null) ...[
                      const SizedBox(height: AppSpacing.md),
                      Text(
                        accessState.failure!.message,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ],
                    const SizedBox(height: AppSpacing.lg),
                    FilledButton(
                      onPressed: accessState.isSubmitting ? null : _submit,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          vertical: AppSpacing.sm,
                        ),
                        child: accessState.isSubmitting
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Text(
                                _isRegistration
                                    ? strings.createAccount
                                    : strings.confirmReset,
                              ),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    TextButton(
                      onPressed: isBusy ? null : () => context.go('/login'),
                      child: Text(strings.backToLogin),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  String? _validateEmail(String? value, AppLocalizations strings) {
    final email = value?.trim() ?? '';
    if (email.isEmpty) {
      return strings.emailRequired;
    }
    if (!email.contains('@')) {
      return strings.emailInvalid;
    }
    return null;
  }

  String? _validatePassword(String? value, AppLocalizations strings) {
    final password = value ?? '';
    if (password.length < 8) {
      return strings.passwordTooShort;
    }
    if (utf8.encode(password).length > 72) {
      return strings.passwordTooLong;
    }
    return null;
  }

  Future<void> _sendCode() async {
    FocusScope.of(context).unfocus();
    if (!(_emailFieldKey.currentState?.validate() ?? false)) {
      return;
    }
    final sent = await ref
        .read(authAccessControllerProvider(widget.purpose).notifier)
        .sendCode(_emailController.text);
    if (sent && mounted) {
      _codeController.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context).codeSentHint)),
      );
    }
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    final succeeded = await ref
        .read(authAccessControllerProvider(widget.purpose).notifier)
        .submit(
          email: _emailController.text,
          code: _codeController.text,
          password: _passwordController.text,
        );
    if (!mounted) {
      return;
    }
    if (!succeeded) {
      _passwordController.clear();
      _confirmPasswordController.clear();
      return;
    }

    final location = Uri(
      path: '/login',
      queryParameters: {
        'email': _emailController.text.trim(),
        'notice': _isRegistration ? 'registered' : 'password-reset',
      },
    );
    context.go(location.toString());
  }
}
