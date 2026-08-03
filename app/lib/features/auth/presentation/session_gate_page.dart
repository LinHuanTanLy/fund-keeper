import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

class SessionGatePage extends ConsumerWidget {
  const SessionGatePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = AppLocalizations.of(context);
    final authState = ref.watch(authSessionControllerProvider);
    final failure = authState.error;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.account_balance_wallet_rounded,
                  size: 64,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const SizedBox(height: AppSpacing.md),
                Text(
                  strings.appTitle,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: AppSpacing.lg),
                if (failure == null)
                  const CircularProgressIndicator()
                else ...[
                  Text(
                    failure is AppFailure
                        ? failure.message
                        : strings.sessionRestoreFailed,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  FilledButton.icon(
                    onPressed: () {
                      ref
                          .read(authSessionControllerProvider.notifier)
                          .retryRestore();
                    },
                    icon: const Icon(Icons.refresh_rounded),
                    label: Text(strings.retry),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
