import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/entry/application/entry_providers.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_formatters.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

class ManualBuyPage extends ConsumerStatefulWidget {
  const ManualBuyPage({super.key});

  @override
  ConsumerState<ManualBuyPage> createState() => _ManualBuyPageState();
}

class _ManualBuyPageState extends ConsumerState<ManualBuyPage> {
  final _formKey = GlobalKey<FormState>();
  final _fundCodeController = TextEditingController();
  final _amountController = TextEditingController();

  String? _selectedAccountId;
  DateTime _submittedDate = DateTime.now();
  SubmittedPeriod _submittedPeriod = SubmittedPeriod.before15;
  String? _requestId;
  bool _submitting = false;
  String? _error;
  ManualBuyResult? _result;

  @override
  void dispose() {
    _fundCodeController.dispose();
    _amountController.dispose();
    super.dispose();
  }

  void _draftChanged() {
    if (_requestId == null && _error == null) {
      return;
    }
    setState(() {
      _requestId = null;
      _error = null;
    });
  }

  Future<void> _selectDate() async {
    final selected = await showDatePicker(
      context: context,
      initialDate: _submittedDate,
      firstDate: DateTime(2000),
      lastDate: DateTime.now(),
    );
    if (selected != null && mounted) {
      setState(() {
        _submittedDate = selected;
        _requestId = null;
        _error = null;
      });
    }
  }

  Future<void> _submit(String accountId) async {
    if (_submitting || !(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
      _requestId ??= ref.read(entryRepositoryProvider).createBuyRequestId();
    });
    final draft = ManualBuyDraft(
      requestId: _requestId!,
      accountId: accountId,
      fundCode: _fundCodeController.text.trim(),
      amount: Decimal.parse(_amountController.text.trim()),
      submittedDate: _submittedDate,
      submittedPeriod: _submittedPeriod,
    );
    try {
      final result = await ref
          .read(entryRepositoryProvider)
          .createManualBuy(draft);
      if (!mounted) {
        return;
      }
      ref.invalidate(portfolioHomeDataProvider);
      ref.invalidate(transactionHistoryControllerProvider);
      setState(() => _result = result);
    } on AppFailure catch (failure) {
      if (mounted) {
        setState(() => _error = failure.message);
      }
    } on Object {
      if (mounted) {
        setState(() => _error = AppLocalizations.of(context).entryFailed);
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    if (_result != null) {
      return _ManualBuySuccess(
        result: _result!,
        strings: strings,
        onComplete: () => context.pop(true),
      );
    }

    final accounts = ref.watch(portfolioAccountsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(strings.manualBuyTitle)),
      body: SafeArea(
        child: accounts.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _LoadError(
            message: error is AppFailure
                ? error.message
                : strings.accountsLoadFailed,
            retryLabel: strings.retry,
            onRetry: () => ref.invalidate(portfolioAccountsProvider),
          ),
          data: (items) {
            if (items.isEmpty) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.xl),
                  child: Text(
                    strings.noActiveAccount,
                    textAlign: TextAlign.center,
                  ),
                ),
              );
            }
            final selectedId =
                _selectedAccountId != null &&
                    items.any((item) => item.id == _selectedAccountId)
                ? _selectedAccountId!
                : items.first.id;
            return Form(
              key: _formKey,
              child: ListView(
                key: const Key('manual-buy-list'),
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.md,
                  AppSpacing.md,
                  AppSpacing.md,
                  AppSpacing.xl,
                ),
                children: [
                  Text(
                    strings.manualBuyDescription,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  DropdownButtonFormField<String>(
                    key: const Key('manual-buy-account'),
                    initialValue: selectedId,
                    decoration: InputDecoration(
                      labelText: strings.platformAccount,
                    ),
                    items: [
                      for (final account in items)
                        DropdownMenuItem(
                          value: account.id,
                          child: Text(account.name),
                        ),
                    ],
                    onChanged: (value) {
                      setState(() {
                        _selectedAccountId = value;
                        _requestId = null;
                        _error = null;
                      });
                    },
                  ),
                  const SizedBox(height: AppSpacing.md),
                  TextFormField(
                    key: const Key('manual-buy-fund-code'),
                    controller: _fundCodeController,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    decoration: InputDecoration(
                      labelText: strings.fundCode,
                      hintText: strings.fundCodeHint,
                    ),
                    onChanged: (_) => _draftChanged(),
                    validator: (value) {
                      return RegExp(r'^\d{6}$').hasMatch(value?.trim() ?? '')
                          ? null
                          : strings.fundCodeInvalid;
                    },
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  TextFormField(
                    key: const Key('manual-buy-amount'),
                    controller: _amountController,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    decoration: InputDecoration(
                      labelText: strings.purchaseAmount,
                      hintText: strings.amountHint,
                      prefixText: '¥ ',
                    ),
                    onChanged: (_) => _draftChanged(),
                    validator: (value) {
                      final text = value?.trim() ?? '';
                      if (!RegExp(r'^\d+(\.\d{1,4})?$').hasMatch(text)) {
                        return strings.amountInvalid;
                      }
                      return Decimal.parse(text) > Decimal.zero
                          ? null
                          : strings.amountInvalid;
                    },
                  ),
                  const SizedBox(height: AppSpacing.md),
                  ListTile(
                    key: const Key('manual-buy-date'),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.sm,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(AppRadius.md),
                      side: const BorderSide(color: AppColors.border),
                    ),
                    title: Text(strings.submittedDate),
                    subtitle: Text(_formatDate(_submittedDate)),
                    trailing: const Icon(Icons.calendar_month_outlined),
                    onTap: _selectDate,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  Text(
                    strings.submittedPeriod,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  SegmentedButton<SubmittedPeriod>(
                    key: const Key('manual-buy-period'),
                    segments: [
                      ButtonSegment(
                        value: SubmittedPeriod.before15,
                        label: Text(strings.before15),
                      ),
                      ButtonSegment(
                        value: SubmittedPeriod.after15,
                        label: Text(strings.after15),
                      ),
                    ],
                    selected: {_submittedPeriod},
                    onSelectionChanged: (selection) {
                      setState(() {
                        _submittedPeriod = selection.single;
                        _requestId = null;
                        _error = null;
                      });
                    },
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: AppSpacing.md),
                    _ErrorBanner(message: _error!),
                  ],
                  const SizedBox(height: AppSpacing.lg),
                  FilledButton(
                    key: const Key('manual-buy-submit'),
                    onPressed: _submitting ? null : () => _submit(selectedId),
                    child: Text(
                      _submitting ? strings.submitting : strings.submitBuy,
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _ManualBuySuccess extends StatelessWidget {
  const _ManualBuySuccess({
    required this.result,
    required this.strings,
    required this.onComplete,
  });

  final ManualBuyResult result;
  final AppLocalizations strings;
  final VoidCallback onComplete;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: Text(strings.manualBuyTitle),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            children: [
              const Spacer(),
              const CircleAvatar(
                radius: 34,
                backgroundColor: AppColors.brand,
                foregroundColor: Colors.white,
                child: Icon(Icons.check_rounded, size: 38),
              ),
              const SizedBox(height: AppSpacing.md),
              Text(
                strings.manualBuySuccess,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppSpacing.lg),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Column(
                    children: [
                      _ResultRow(
                        label: strings.fundCode,
                        value: '${result.fundName} (${result.fundCode})',
                      ),
                      _ResultRow(
                        label: strings.purchaseAmount,
                        value: PortfolioFormatters.money(result.amount),
                      ),
                      _ResultRow(
                        label: strings.transactionStatus,
                        value: _transactionStatus(strings, result.status),
                      ),
                      _ResultRow(
                        label: strings.effectiveTradeDate,
                        value: result.effectiveTradeDate == null
                            ? '--'
                            : _formatDate(result.effectiveTradeDate!),
                      ),
                      _ResultRow(
                        label: strings.estimatedShares,
                        value: result.shares?.toString() ?? '--',
                      ),
                    ],
                  ),
                ),
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  key: const Key('manual-buy-complete'),
                  onPressed: onComplete,
                  child: Text(strings.complete),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ResultRow extends StatelessWidget {
  const _ResultRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          const SizedBox(width: AppSpacing.md),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({
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

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: AppColors.profit.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.profit.withValues(alpha: 0.3)),
      ),
      child: Text(message, style: const TextStyle(color: AppColors.profit)),
    );
  }
}

String _formatDate(DateTime date) {
  String two(int value) => value.toString().padLeft(2, '0');
  return '${date.year}-${two(date.month)}-${two(date.day)}';
}

String _transactionStatus(AppLocalizations strings, String status) {
  return switch (status) {
    'ESTIMATED' => strings.statusEstimated,
    'PENDING' => strings.statusPending,
    'CONFIRMED' => strings.statusConfirmed,
    _ => status,
  };
}
