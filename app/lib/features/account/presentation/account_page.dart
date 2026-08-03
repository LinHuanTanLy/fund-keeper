import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/account/application/account_providers.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/auth/application/auth_providers.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

class AccountPage extends ConsumerStatefulWidget {
  const AccountPage({super.key});

  @override
  ConsumerState<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends ConsumerState<AccountPage> {
  final _actingIds = <String>{};
  bool _creating = false;

  Future<void> _createAccount() async {
    if (_creating) {
      return;
    }
    final draft = await showModalBottomSheet<AccountDraft>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => const _AccountEditorSheet(),
    );
    if (draft == null || !mounted) {
      return;
    }
    setState(() => _creating = true);
    try {
      await ref
          .read(accountManagementControllerProvider.notifier)
          .createAccount(draft);
      _invalidateAccountConsumers();
      if (mounted) {
        _showMessage(AppLocalizations.of(context).accountCreated);
      }
    } on AppFailure catch (failure) {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(failure.message, error: true);
      }
    } on Object {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(
          AppLocalizations.of(context).accountActionFailed,
          error: true,
        );
      }
    } finally {
      if (mounted) {
        setState(() => _creating = false);
      }
    }
  }

  Future<void> _editAccount(PortfolioAccount account) async {
    if (_actingIds.contains(account.id)) {
      return;
    }
    final draft = await showModalBottomSheet<AccountDraft>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => _AccountEditorSheet(account: account),
    );
    if (draft == null || !mounted) {
      return;
    }
    setState(() => _actingIds.add(account.id));
    try {
      await ref
          .read(accountManagementControllerProvider.notifier)
          .updateAccount(account.id, draft);
      _invalidateAccountConsumers();
      if (mounted) {
        _showMessage(AppLocalizations.of(context).accountUpdated);
      }
    } on AppFailure catch (failure) {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(failure.message, error: true);
      }
    } on Object {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(
          AppLocalizations.of(context).accountActionFailed,
          error: true,
        );
      }
    } finally {
      if (mounted) {
        setState(() => _actingIds.remove(account.id));
      }
    }
  }

  Future<void> _archiveAccount(PortfolioAccount account) async {
    if (_actingIds.contains(account.id)) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _ArchiveAccountDialog(account: account),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    setState(() => _actingIds.add(account.id));
    try {
      await ref
          .read(accountManagementControllerProvider.notifier)
          .archiveAccount(account.id);
      if (ref.read(selectedPortfolioAccountProvider) == account.id) {
        ref.read(selectedPortfolioAccountProvider.notifier).select(null);
      }
      _invalidateAccountConsumers();
      if (mounted) {
        _showMessage(AppLocalizations.of(context).accountArchivedSuccess);
      }
    } on AppFailure catch (failure) {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(failure.message, error: true);
      }
    } on Object {
      await _refreshAfterFailure();
      if (mounted) {
        _showMessage(
          AppLocalizations.of(context).accountActionFailed,
          error: true,
        );
      }
    } finally {
      if (mounted) {
        setState(() => _actingIds.remove(account.id));
      }
    }
  }

  Future<void> _refreshAfterFailure() {
    return ref.read(accountManagementControllerProvider.notifier).refresh();
  }

  void _invalidateAccountConsumers() {
    ref.invalidate(portfolioAccountsProvider);
    ref.invalidate(portfolioHomeDataProvider);
    ref.invalidate(transactionHistoryControllerProvider);
  }

  void _showMessage(String message, {bool error = false}) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: error ? AppColors.profit : null,
        ),
      );
  }

  Future<void> _logout() async {
    final messenger = ScaffoldMessenger.of(context);
    final strings = AppLocalizations.of(context);
    try {
      await ref.read(authSessionControllerProvider.notifier).logout();
    } on Object {
      messenger.showSnackBar(
        SnackBar(content: Text(strings.logoutRevocationFailed)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final session = ref.watch(authSessionControllerProvider).value;
    final email = session?.user.email ?? '';
    final avatarText = email.isEmpty
        ? '?'
        : email.substring(0, 1).toUpperCase();
    final accounts = ref.watch(accountManagementControllerProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              strings.profileTab,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: AppSpacing.md),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.md),
                child: Row(
                  children: [
                    CircleAvatar(child: Text(avatarText)),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            strings.loggedInAccount,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                          const SizedBox(height: AppSpacing.xxs),
                          Text(
                            email,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        strings.platformAccounts,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: AppSpacing.xxs),
                      Text(
                        strings.platformAccountsDescription,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
                IconButton.filled(
                  key: const Key('account-add'),
                  tooltip: strings.addPlatformAccount,
                  onPressed: _creating ? null : _createAccount,
                  icon: _creating
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.add_rounded),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Expanded(
              child: accounts.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, _) => _AccountLoadError(
                  message: error is AppFailure
                      ? error.message
                      : strings.accountListLoadFailed,
                  retryLabel: strings.retry,
                  onRetry: () =>
                      ref.invalidate(accountManagementControllerProvider),
                ),
                data: (items) {
                  final sorted = [...items]
                    ..sort((a, b) {
                      final status = a.status.compareTo(b.status);
                      return status != 0 ? status : a.name.compareTo(b.name);
                    });
                  final activeCount = items
                      .where((account) => account.status == 'ACTIVE')
                      .length;
                  return RefreshIndicator(
                    onRefresh: () => ref
                        .read(accountManagementControllerProvider.notifier)
                        .refresh(),
                    child: ListView(
                      key: const Key('account-management-list'),
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.only(bottom: AppSpacing.md),
                      children: [
                        Text(
                          strings.activeAccountCount(activeCount),
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        for (final account in sorted) ...[
                          _AccountCard(
                            account: account,
                            strings: strings,
                            acting: _actingIds.contains(account.id),
                            canArchive: activeCount > 1,
                            onEdit: () => _editAccount(account),
                            onArchive: () => _archiveAccount(account),
                          ),
                          const SizedBox(height: AppSpacing.sm),
                        ],
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            OutlinedButton.icon(
              key: const Key('account-logout'),
              onPressed: _logout,
              icon: const Icon(Icons.logout_rounded),
              label: Text(strings.logout),
            ),
          ],
        ),
      ),
    );
  }
}

class _AccountCard extends StatelessWidget {
  const _AccountCard({
    required this.account,
    required this.strings,
    required this.acting,
    required this.canArchive,
    required this.onEdit,
    required this.onArchive,
  });

  final PortfolioAccount account;
  final AppLocalizations strings;
  final bool acting;
  final bool canArchive;
  final VoidCallback onEdit;
  final VoidCallback onArchive;

  @override
  Widget build(BuildContext context) {
    final archived = account.status == 'ARCHIVED';
    return Card(
      key: Key('account-card-${account.id}'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: archived
                      ? AppColors.border
                      : AppColors.brand.withValues(alpha: 0.14),
                  foregroundColor: archived
                      ? AppColors.secondaryText
                      : AppColors.brandDark,
                  child: Icon(_platformIcon(account.platform)),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        account.name,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: AppSpacing.xxs),
                      Text(
                        _platformLabel(strings, account.platform),
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
                Chip(
                  label: Text(
                    archived ? strings.accountArchived : strings.accountActive,
                  ),
                ),
              ],
            ),
            if (archived) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                strings.accountArchivedHint,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ] else if (acting) ...[
              const SizedBox(height: AppSpacing.sm),
              const LinearProgressIndicator(),
            ] else ...[
              const Divider(height: AppSpacing.lg),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton.icon(
                    key: Key('account-edit-${account.id}'),
                    onPressed: onEdit,
                    icon: const Icon(Icons.edit_outlined),
                    label: Text(strings.editAccount),
                  ),
                  const SizedBox(width: AppSpacing.xs),
                  TextButton.icon(
                    key: Key('account-archive-${account.id}'),
                    onPressed: canArchive ? onArchive : null,
                    icon: const Icon(Icons.archive_outlined),
                    label: Text(strings.archiveAccount),
                  ),
                ],
              ),
              if (!canArchive)
                Align(
                  alignment: Alignment.centerRight,
                  child: Text(
                    strings.lastActiveAccountHint,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _AccountEditorSheet extends StatefulWidget {
  const _AccountEditorSheet({this.account});

  final PortfolioAccount? account;

  @override
  State<_AccountEditorSheet> createState() => _AccountEditorSheetState();
}

class _AccountEditorSheetState extends State<_AccountEditorSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late AccountPlatform _platform;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.account?.name);
    _platform = AccountPlatform.fromApiValue(
      widget.account?.platform ?? 'OTHER',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppSpacing.md,
        0,
        AppSpacing.md,
        MediaQuery.viewInsetsOf(context).bottom + AppSpacing.lg,
      ),
      child: SafeArea(
        child: Form(
          key: _formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  widget.account == null
                      ? strings.addPlatformAccount
                      : strings.editPlatformAccount,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  key: const Key('account-editor-name'),
                  controller: _nameController,
                  autofocus: true,
                  maxLength: 100,
                  decoration: InputDecoration(
                    labelText: strings.accountName,
                    hintText: strings.accountNameHint,
                  ),
                  validator: (value) {
                    final length = value?.trim().length ?? 0;
                    return length >= 1 && length <= 100
                        ? null
                        : strings.accountNameInvalid;
                  },
                ),
                const SizedBox(height: AppSpacing.sm),
                DropdownButtonFormField<AccountPlatform>(
                  key: const Key('account-editor-platform'),
                  initialValue: _platform,
                  decoration: InputDecoration(
                    labelText: strings.accountPlatform,
                  ),
                  items: [
                    for (final platform in AccountPlatform.values)
                      DropdownMenuItem(
                        value: platform,
                        child: Text(_platformLabel(strings, platform.apiValue)),
                      ),
                  ],
                  onChanged: (value) {
                    if (value != null) {
                      setState(() => _platform = value);
                    }
                  },
                ),
                const SizedBox(height: AppSpacing.lg),
                FilledButton(
                  key: const Key('account-editor-submit'),
                  onPressed: () {
                    if (!(_formKey.currentState?.validate() ?? false)) {
                      return;
                    }
                    Navigator.pop(
                      context,
                      AccountDraft(
                        name: _nameController.text.trim(),
                        platform: _platform,
                      ),
                    );
                  },
                  child: Text(strings.save),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ArchiveAccountDialog extends StatelessWidget {
  const _ArchiveAccountDialog({required this.account});

  final PortfolioAccount account;

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(strings.archiveAccount),
      content: Text('${account.name}\n\n${strings.archiveAccountDescription}'),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
        ),
        FilledButton(
          key: const Key('account-archive-submit'),
          onPressed: () => Navigator.pop(context, true),
          child: Text(strings.archiveAccount),
        ),
      ],
    );
  }
}

class _AccountLoadError extends StatelessWidget {
  const _AccountLoadError({
    required this.message,
    required this.retryLabel,
    required this.onRetry,
  });

  final String message;
  final String retryLabel;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message),
          const SizedBox(height: AppSpacing.md),
          FilledButton(onPressed: onRetry, child: Text(retryLabel)),
        ],
      ),
    );
  }
}

String _platformLabel(AppLocalizations strings, String platform) {
  return switch (platform) {
    'ALIPAY' => strings.platformAlipay,
    'TIANTIAN_FUND' => strings.platformTiantianFund,
    'BANK' => strings.platformBank,
    _ => strings.platformOther,
  };
}

IconData _platformIcon(String platform) {
  return switch (platform) {
    'ALIPAY' => Icons.account_balance_wallet_outlined,
    'TIANTIAN_FUND' => Icons.show_chart_rounded,
    'BANK' => Icons.account_balance_outlined,
    _ => Icons.folder_outlined,
  };
}
