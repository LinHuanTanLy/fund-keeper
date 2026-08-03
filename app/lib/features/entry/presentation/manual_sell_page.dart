import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/entry/application/entry_providers.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_formatters.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

class ManualSellPage extends ConsumerStatefulWidget {
  const ManualSellPage({super.key});

  @override
  ConsumerState<ManualSellPage> createState() => _ManualSellPageState();
}

class _ManualSellPageState extends ConsumerState<ManualSellPage> {
  final _formKey = GlobalKey<FormState>();
  final _expectedAmountController = TextEditingController();
  final _noteController = TextEditingController();

  String? _selectedAccountId;
  String? _selectedFundCode;
  SellMode _sellMode = SellMode.partial;
  DateTime _submittedDate = DateTime.now();
  SubmittedPeriod _submittedPeriod = SubmittedPeriod.before15;
  String? _requestId;
  bool _submitting = false;
  String? _error;
  ManualSellResult? _result;

  @override
  void dispose() {
    _expectedAmountController.dispose();
    _noteController.dispose();
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

  Future<void> _submit(String accountId, FundPortfolioCard fund) async {
    if (_submitting ||
        fund.openSellCount > 0 ||
        !(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
      _requestId ??= ref.read(entryRepositoryProvider).createSellRequestId();
    });
    final note = _noteController.text.trim();
    final draft = ManualSellDraft(
      requestId: _requestId!,
      accountId: accountId,
      fundCode: fund.fundCode,
      sellMode: _sellMode,
      expectedAmount: _sellMode == SellMode.partial
          ? Decimal.parse(_expectedAmountController.text.trim())
          : null,
      submittedDate: _submittedDate,
      submittedPeriod: _submittedPeriod,
      note: note.isEmpty ? null : note,
    );
    try {
      final result = await ref
          .read(entryRepositoryProvider)
          .createManualSell(draft);
      if (!mounted) {
        return;
      }
      ref.invalidate(portfolioHomeDataProvider);
      ref.invalidate(portfolioFundsProvider(accountId));
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
      return _ManualSellSuccess(
        result: _result!,
        strings: strings,
        onComplete: () => context.pop(true),
      );
    }

    final accounts = ref.watch(portfolioAccountsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(strings.manualSellTitle)),
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
            final selectedAccountId =
                _selectedAccountId != null &&
                    items.any((item) => item.id == _selectedAccountId)
                ? _selectedAccountId!
                : items.first.id;
            final funds = ref.watch(portfolioFundsProvider(selectedAccountId));
            return Form(
              key: _formKey,
              child: ListView(
                key: const Key('manual-sell-list'),
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.md,
                  AppSpacing.md,
                  AppSpacing.md,
                  AppSpacing.xl,
                ),
                children: [
                  Text(
                    strings.manualSellDescription,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  DropdownButtonFormField<String>(
                    key: const Key('manual-sell-account'),
                    initialValue: selectedAccountId,
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
                        _selectedFundCode = null;
                        _requestId = null;
                        _error = null;
                      });
                    },
                  ),
                  const SizedBox(height: AppSpacing.md),
                  funds.when(
                    loading: () => const LinearProgressIndicator(
                      key: Key('manual-sell-funds-loading'),
                    ),
                    error: (error, _) => _LoadError(
                      message: error is AppFailure
                          ? error.message
                          : strings.positionsLoadFailed,
                      retryLabel: strings.retry,
                      onRetry: () => ref.invalidate(
                        portfolioFundsProvider(selectedAccountId),
                      ),
                    ),
                    data: (allFunds) {
                      final sellableFunds = allFunds
                          .where(
                            (fund) =>
                                fund.hasCurrentPosition &&
                                fund.totalShares != null &&
                                fund.totalShares! > Decimal.zero,
                          )
                          .toList(growable: false);
                      if (sellableFunds.isEmpty) {
                        return _EmptyPositions(
                          message: strings.noSellablePosition,
                        );
                      }
                      final selectedFundCode =
                          _selectedFundCode != null &&
                              sellableFunds.any(
                                (fund) => fund.fundCode == _selectedFundCode,
                              )
                          ? _selectedFundCode!
                          : sellableFunds.first.fundCode;
                      final selectedFund = sellableFunds.firstWhere(
                        (fund) => fund.fundCode == selectedFundCode,
                      );
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          DropdownButtonFormField<String>(
                            key: ValueKey(
                              'manual-sell-fund-$selectedAccountId-'
                              '$selectedFundCode',
                            ),
                            initialValue: selectedFundCode,
                            decoration: InputDecoration(
                              labelText: strings.holdingFund,
                            ),
                            items: [
                              for (final fund in sellableFunds)
                                DropdownMenuItem(
                                  value: fund.fundCode,
                                  child: Text(
                                    '${fund.fundName} (${fund.fundCode})',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                            ],
                            onChanged: (value) {
                              setState(() {
                                _selectedFundCode = value;
                                _requestId = null;
                                _error = null;
                              });
                            },
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          _PositionSummary(
                            fund: selectedFund,
                            strings: strings,
                          ),
                          if (selectedFund.openSellCount > 0) ...[
                            const SizedBox(height: AppSpacing.sm),
                            _WarningBanner(
                              key: const Key('manual-sell-open-blocked'),
                              message: strings.openSellBlocked,
                            ),
                          ],
                          const SizedBox(height: AppSpacing.lg),
                          Text(
                            strings.sellMode,
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          SegmentedButton<SellMode>(
                            key: const Key('manual-sell-mode'),
                            segments: [
                              ButtonSegment(
                                value: SellMode.partial,
                                label: Text(strings.partialSell),
                              ),
                              ButtonSegment(
                                value: SellMode.full,
                                label: Text(strings.fullSell),
                              ),
                            ],
                            selected: {_sellMode},
                            onSelectionChanged: (selection) {
                              setState(() {
                                _sellMode = selection.single;
                                _requestId = null;
                                _error = null;
                              });
                            },
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          Text(
                            _sellMode == SellMode.partial
                                ? strings.partialSellHint
                                : strings.fullSellHint,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                          if (_sellMode == SellMode.partial) ...[
                            const SizedBox(height: AppSpacing.md),
                            TextFormField(
                              key: const Key('manual-sell-expected-amount'),
                              controller: _expectedAmountController,
                              keyboardType:
                                  const TextInputType.numberWithOptions(
                                    decimal: true,
                                  ),
                              decoration: InputDecoration(
                                labelText: strings.expectedReceivedAmount,
                                hintText: strings.expectedReceivedAmountHint,
                                prefixText: '¥ ',
                              ),
                              onChanged: (_) => _draftChanged(),
                              validator: (value) {
                                final text = value?.trim() ?? '';
                                if (!RegExp(
                                  r'^\d+(\.\d{1,4})?$',
                                ).hasMatch(text)) {
                                  return strings.amountInvalid;
                                }
                                return Decimal.parse(text) > Decimal.zero
                                    ? null
                                    : strings.amountInvalid;
                              },
                            ),
                          ],
                          const SizedBox(height: AppSpacing.md),
                          ListTile(
                            key: const Key('manual-sell-date'),
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
                            key: const Key('manual-sell-period'),
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
                          const SizedBox(height: AppSpacing.md),
                          TextField(
                            key: const Key('manual-sell-note'),
                            controller: _noteController,
                            maxLength: 500,
                            decoration: InputDecoration(
                              labelText: strings.sellNote,
                              hintText: strings.sellNoteHint,
                            ),
                            onChanged: (_) => _draftChanged(),
                          ),
                          if (_error != null) ...[
                            const SizedBox(height: AppSpacing.sm),
                            _ErrorBanner(message: _error!),
                          ],
                          const SizedBox(height: AppSpacing.lg),
                          FilledButton(
                            key: const Key('manual-sell-submit'),
                            onPressed:
                                _submitting || selectedFund.openSellCount > 0
                                ? null
                                : () =>
                                      _submit(selectedAccountId, selectedFund),
                            child: Text(
                              _submitting
                                  ? strings.submitting
                                  : strings.submitSell,
                            ),
                          ),
                        ],
                      );
                    },
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

class _PositionSummary extends StatelessWidget {
  const _PositionSummary({required this.fund, required this.strings});

  final FundPortfolioCard fund;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.sm),
        child: Row(
          children: [
            Expanded(
              child: _Metric(
                label: strings.positionShares,
                value: fund.totalShares?.toString() ?? '--',
              ),
            ),
            Expanded(
              child: _Metric(
                label: strings.positionMarketValue,
                value: PortfolioFormatters.money(fund.currentMarketValue),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ManualSellSuccess extends StatelessWidget {
  const _ManualSellSuccess({
    required this.result,
    required this.strings,
    required this.onComplete,
  });

  final ManualSellResult result;
  final AppLocalizations strings;
  final VoidCallback onComplete;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: Text(strings.manualSellTitle),
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
                strings.manualSellSuccess,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppSpacing.lg),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Column(
                    children: [
                      _ResultRow(
                        label: strings.holdingFund,
                        value: '${result.fundName} (${result.fundCode})',
                      ),
                      _ResultRow(
                        label: strings.sellMode,
                        value: result.sellMode == 'FULL'
                            ? strings.fullSell
                            : strings.partialSell,
                      ),
                      if (result.expectedAmount != null)
                        _ResultRow(
                          label: strings.expectedReceivedAmount,
                          value: PortfolioFormatters.money(
                            result.expectedAmount,
                          ),
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
                        label: strings.estimatedSoldShares,
                        value: result.shares?.toString() ?? '--',
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                strings.sellResultHint,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  key: const Key('manual-sell-complete'),
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

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
        const SizedBox(height: AppSpacing.xxs),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
      ],
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
          const SizedBox(height: AppSpacing.sm),
          FilledButton(onPressed: onRetry, child: Text(retryLabel)),
        ],
      ),
    );
  }
}

class _EmptyPositions extends StatelessWidget {
  const _EmptyPositions({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Text(message, textAlign: TextAlign.center),
      ),
    );
  }
}

class _WarningBanner extends StatelessWidget {
  const _WarningBanner({required this.message, super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: AppColors.warning.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.warning.withValues(alpha: 0.35)),
      ),
      child: Text(message, style: const TextStyle(color: AppColors.warning)),
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
