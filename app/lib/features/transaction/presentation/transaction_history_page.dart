import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_formatters.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

const _allFilter = '__all__';

class TransactionHistoryPage extends ConsumerStatefulWidget {
  const TransactionHistoryPage({super.key});

  @override
  ConsumerState<TransactionHistoryPage> createState() =>
      _TransactionHistoryPageState();
}

class _TransactionHistoryPageState
    extends ConsumerState<TransactionHistoryPage> {
  final _fundCodeController = TextEditingController();
  final _actingIds = <String>{};

  @override
  void dispose() {
    _fundCodeController.dispose();
    super.dispose();
  }

  Future<void> _applyFilters(TransactionFilters filters) {
    return ref
        .read(transactionHistoryControllerProvider.notifier)
        .applyFilters(filters);
  }

  Future<void> _confirmTransaction(TransactionRecord transaction) async {
    if (_actingIds.contains(transaction.id)) {
      return;
    }
    final draft = await showModalBottomSheet<Object>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => transaction.isBuy
          ? _BuyConfirmationSheet(transaction: transaction)
          : _SellConfirmationSheet(transaction: transaction),
    );
    if (draft == null || !mounted) {
      return;
    }
    setState(() => _actingIds.add(transaction.id));
    try {
      final repository = ref.read(transactionRepositoryProvider);
      if (draft is BuyConfirmationDraft) {
        await repository.confirmBuy(transaction.id, draft);
      } else if (draft is SellConfirmationDraft) {
        await repository.confirmSell(transaction.id, draft);
      } else {
        return;
      }
      if (!mounted) {
        return;
      }
      ref.invalidate(portfolioHomeDataProvider);
      await ref.read(transactionHistoryControllerProvider.notifier).refresh();
      if (mounted) {
        _showMessage(AppLocalizations.of(context).transactionConfirmed);
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        _showMessage(failure.message, error: true);
      }
    } on Object {
      if (mounted) {
        _showMessage(
          AppLocalizations.of(context).transactionActionFailed,
          error: true,
        );
      }
    } finally {
      if (mounted) {
        setState(() => _actingIds.remove(transaction.id));
      }
    }
  }

  Future<void> _cancelTransaction(TransactionRecord transaction) async {
    if (_actingIds.contains(transaction.id)) {
      return;
    }
    final decision = await showDialog<_CancellationDecision>(
      context: context,
      builder: (context) => const _CancellationDialog(),
    );
    if (decision == null || !mounted) {
      return;
    }
    setState(() => _actingIds.add(transaction.id));
    try {
      await ref
          .read(transactionRepositoryProvider)
          .cancel(transaction.id, decision.reason);
      if (!mounted) {
        return;
      }
      ref.invalidate(portfolioHomeDataProvider);
      await ref.read(transactionHistoryControllerProvider.notifier).refresh();
      if (mounted) {
        _showMessage(AppLocalizations.of(context).transactionCancelled);
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        _showMessage(failure.message, error: true);
      }
    } on Object {
      if (mounted) {
        _showMessage(
          AppLocalizations.of(context).transactionActionFailed,
          error: true,
        );
      }
    } finally {
      if (mounted) {
        setState(() => _actingIds.remove(transaction.id));
      }
    }
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

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final history = ref.watch(transactionHistoryControllerProvider);
    final accounts = ref.watch(portfolioAccountsProvider);

    return SafeArea(
      child: history.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _HistoryError(
          message: error is AppFailure
              ? error.message
              : strings.transactionLoadFailed,
          retryLabel: strings.retry,
          onRetry: () => ref.invalidate(transactionHistoryControllerProvider),
        ),
        data: (state) => RefreshIndicator(
          onRefresh: () =>
              ref.read(transactionHistoryControllerProvider.notifier).refresh(),
          child: ListView(
            key: const Key('transaction-history-list'),
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.md,
              AppSpacing.md,
              AppSpacing.md,
              112,
            ),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      strings.transactionRecords,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  Text(
                    strings.transactionRecordCount(state.totalElements),
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              _TransactionFiltersCard(
                filters: state.filters,
                accounts: accounts,
                fundCodeController: _fundCodeController,
                strings: strings,
                onChanged: _applyFilters,
              ),
              if (state.isRefreshing) ...[
                const SizedBox(height: AppSpacing.sm),
                const LinearProgressIndicator(),
              ],
              const SizedBox(height: AppSpacing.md),
              if (state.items.isEmpty)
                _EmptyTransactions(strings: strings)
              else
                for (final transaction in state.items) ...[
                  _TransactionCard(
                    transaction: transaction,
                    strings: strings,
                    acting: _actingIds.contains(transaction.id),
                    onConfirm: () => _confirmTransaction(transaction),
                    onCancel: () => _cancelTransaction(transaction),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                ],
              if (state.loadMoreError != null) ...[
                Text(
                  state.loadMoreError!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.profit),
                ),
                const SizedBox(height: AppSpacing.xs),
              ],
              if (state.hasNextPage)
                OutlinedButton(
                  key: const Key('transaction-load-more'),
                  onPressed: state.isLoadingMore
                      ? null
                      : () => ref
                            .read(transactionHistoryControllerProvider.notifier)
                            .loadMore(),
                  child: Text(
                    state.isLoadingMore
                        ? strings.loadingMore
                        : strings.loadMore,
                  ),
                )
              else if (state.items.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Text(
                    strings.noMoreRecords,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TransactionFiltersCard extends StatelessWidget {
  const _TransactionFiltersCard({
    required this.filters,
    required this.accounts,
    required this.fundCodeController,
    required this.strings,
    required this.onChanged,
  });

  final TransactionFilters filters;
  final AsyncValue<List<PortfolioAccount>> accounts;
  final TextEditingController fundCodeController;
  final AppLocalizations strings;
  final Future<void> Function(TransactionFilters filters) onChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: accounts.when(
                    loading: () => const LinearProgressIndicator(),
                    error: (_, _) => Text(strings.accountsLoadFailed),
                    data: (items) => DropdownButtonFormField<String>(
                      key: ValueKey(
                        'transaction-account-filter-'
                        '${filters.accountId ?? _allFilter}',
                      ),
                      isExpanded: true,
                      initialValue: filters.accountId ?? _allFilter,
                      decoration: InputDecoration(
                        labelText: strings.platformAccount,
                      ),
                      items: [
                        DropdownMenuItem(
                          value: _allFilter,
                          child: _FilterLabel(strings.allAccounts),
                        ),
                        for (final account in items)
                          DropdownMenuItem(
                            value: account.id,
                            child: _FilterLabel(account.name),
                          ),
                      ],
                      onChanged: (value) {
                        onChanged(
                          filters.copyWith(
                            accountId: value,
                            clearAccount: value == _allFilter,
                          ),
                        );
                      },
                    ),
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: DropdownButtonFormField<String>(
                    key: ValueKey(
                      'transaction-type-filter-${filters.type ?? _allFilter}',
                    ),
                    isExpanded: true,
                    initialValue: filters.type ?? _allFilter,
                    decoration: InputDecoration(
                      labelText: strings.transactionType,
                    ),
                    items: [
                      DropdownMenuItem(
                        value: _allFilter,
                        child: _FilterLabel(strings.allTypes),
                      ),
                      DropdownMenuItem(
                        value: 'BUY',
                        child: _FilterLabel(strings.transactionBuy),
                      ),
                      DropdownMenuItem(
                        value: 'SELL',
                        child: _FilterLabel(strings.transactionSell),
                      ),
                      DropdownMenuItem(
                        value: 'POSITION_ADJUSTMENT',
                        child: _FilterLabel(strings.transactionAdjustment),
                      ),
                    ],
                    onChanged: (value) {
                      onChanged(
                        filters.copyWith(
                          type: value,
                          clearType: value == _allFilter,
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Expanded(
                  child: DropdownButtonFormField<String>(
                    key: ValueKey(
                      'transaction-status-filter-'
                      '${filters.status ?? _allFilter}',
                    ),
                    isExpanded: true,
                    initialValue: filters.status ?? _allFilter,
                    decoration: InputDecoration(
                      labelText: strings.transactionStatus,
                    ),
                    items: [
                      DropdownMenuItem(
                        value: _allFilter,
                        child: _FilterLabel(strings.allStatuses),
                      ),
                      for (final status in const [
                        'PENDING',
                        'ESTIMATED',
                        'CONFIRMED',
                        'NEEDS_CALIBRATION',
                        'CANCELLED',
                        'REVERSED',
                      ])
                        DropdownMenuItem(
                          value: status,
                          child: _FilterLabel(_statusLabel(strings, status)),
                        ),
                    ],
                    onChanged: (value) {
                      onChanged(
                        filters.copyWith(
                          status: value,
                          clearStatus: value == _allFilter,
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: TextField(
                    key: const Key('transaction-fund-filter'),
                    controller: fundCodeController,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      labelText: strings.fundCodeFilter,
                      counterText: '',
                      suffixIcon: IconButton(
                        tooltip: strings.filter,
                        onPressed: () => _searchFund(filters),
                        icon: const Icon(Icons.search_rounded),
                      ),
                    ),
                    onSubmitted: (_) => _searchFund(filters),
                  ),
                ),
              ],
            ),
            if (filters.accountId != null ||
                filters.fundCode != null ||
                filters.type != null ||
                filters.status != null)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () {
                    fundCodeController.clear();
                    onChanged(const TransactionFilters());
                  },
                  child: Text(strings.clearFilters),
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _searchFund(TransactionFilters filters) {
    final value = fundCodeController.text.trim();
    onChanged(filters.copyWith(fundCode: value, clearFundCode: value.isEmpty));
  }
}

class _FilterLabel extends StatelessWidget {
  const _FilterLabel(this.value);

  final String value;

  @override
  Widget build(BuildContext context) {
    return Text(value, maxLines: 1, overflow: TextOverflow.ellipsis);
  }
}

class _TransactionCard extends StatelessWidget {
  const _TransactionCard({
    required this.transaction,
    required this.strings,
    required this.acting,
    required this.onConfirm,
    required this.onCancel,
  });

  final TransactionRecord transaction;
  final AppLocalizations strings;
  final bool acting;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(transaction.status);
    final primaryAmount = transaction.isBuy
        ? transaction.amount
        : transaction.actualReceivedAmount ?? transaction.expectedAmount;
    return Card(
      key: Key('transaction-${transaction.id}'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  backgroundColor: _typeColor(
                    transaction.type,
                  ).withValues(alpha: 0.12),
                  foregroundColor: _typeColor(transaction.type),
                  child: Icon(_typeIcon(transaction.type)),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        transaction.fundName,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: AppSpacing.xxs),
                      Text(
                        '${transaction.fundCode} · ${transaction.accountName}',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.xs,
                    vertical: AppSpacing.xxs,
                  ),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(AppRadius.pill),
                  ),
                  child: Text(
                    _statusLabel(strings, transaction.status),
                    style: TextStyle(
                      color: statusColor,
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                Expanded(
                  child: _RecordMetric(
                    label: _typeLabel(strings, transaction.type),
                    value: PortfolioFormatters.money(primaryAmount),
                  ),
                ),
                Expanded(
                  child: _RecordMetric(
                    label: strings.estimatedShares,
                    value: transaction.shares?.toString() ?? '--',
                  ),
                ),
              ],
            ),
            if (transaction.isSell && transaction.realizedProfit != null) ...[
              const SizedBox(height: AppSpacing.sm),
              _RecordMetric(
                label: strings.realizedProfit,
                value: PortfolioFormatters.money(
                  transaction.realizedProfit,
                  signed: true,
                ),
                color: PortfolioFormatters.valueColor(
                  transaction.realizedProfit,
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.sm),
            Text(
              '${strings.submittedTime}: '
              '${_date(transaction.submittedDate)} '
              '${_periodLabel(strings, transaction.submittedPeriod)}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            if (transaction.pendingReason != null) ...[
              const SizedBox(height: AppSpacing.xxs),
              Text(
                _pendingReason(strings, transaction.pendingReason!),
                style: const TextStyle(color: AppColors.warning, fontSize: 12),
              ),
            ],
            if (transaction.cancellationReason != null) ...[
              const SizedBox(height: AppSpacing.xxs),
              Text(
                transaction.cancellationReason!,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
            if (transaction.canConfirm || transaction.canCancel) ...[
              const Divider(height: AppSpacing.lg),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  if (acting)
                    const Padding(
                      padding: EdgeInsets.all(AppSpacing.sm),
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    )
                  else ...[
                    TextButton(
                      key: Key('transaction-cancel-${transaction.id}'),
                      onPressed: onCancel,
                      child: Text(strings.cancelTransaction),
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    FilledButton(
                      key: Key('transaction-confirm-${transaction.id}'),
                      onPressed: onConfirm,
                      child: Text(strings.confirmTransaction),
                    ),
                  ],
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RecordMetric extends StatelessWidget {
  const _RecordMetric({
    required this.label,
    required this.value,
    this.color = AppColors.primaryText,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
        const SizedBox(height: AppSpacing.xxs),
        Text(
          value,
          style: TextStyle(color: color, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }
}

class _BuyConfirmationSheet extends StatefulWidget {
  const _BuyConfirmationSheet({required this.transaction});

  final TransactionRecord transaction;

  @override
  State<_BuyConfirmationSheet> createState() => _BuyConfirmationSheetState();
}

class _BuyConfirmationSheetState extends State<_BuyConfirmationSheet> {
  final _formKey = GlobalKey<FormState>();
  final _sharesController = TextEditingController();
  DateTime _confirmedDate = DateTime.now();

  @override
  void dispose() {
    _sharesController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    return _ConfirmationSheetFrame(
      title: strings.confirmTransaction,
      formKey: _formKey,
      confirmedDate: _confirmedDate,
      onDateChanged: (date) => setState(() => _confirmedDate = date),
      onSubmit: () {
        if (!(_formKey.currentState?.validate() ?? false)) {
          return;
        }
        Navigator.pop(
          context,
          BuyConfirmationDraft(
            confirmedShares: Decimal.parse(_sharesController.text.trim()),
            confirmedDate: _confirmedDate,
          ),
        );
      },
      children: [
        Text('${widget.transaction.fundName} (${widget.transaction.fundCode})'),
        const SizedBox(height: AppSpacing.md),
        TextFormField(
          key: const Key('buy-confirm-shares'),
          controller: _sharesController,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            labelText: strings.confirmedShares,
            hintText: strings.confirmedSharesHint,
          ),
          validator: (value) => _decimalValidator(
            value,
            scale: 8,
            error: strings.confirmedSharesInvalid,
          ),
        ),
      ],
    );
  }
}

class _SellConfirmationSheet extends StatefulWidget {
  const _SellConfirmationSheet({required this.transaction});

  final TransactionRecord transaction;

  @override
  State<_SellConfirmationSheet> createState() => _SellConfirmationSheetState();
}

class _SellConfirmationSheetState extends State<_SellConfirmationSheet> {
  final _formKey = GlobalKey<FormState>();
  final _receivedController = TextEditingController();
  final _sharesController = TextEditingController();
  DateTime _confirmedDate = DateTime.now();

  @override
  void dispose() {
    _receivedController.dispose();
    _sharesController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    return _ConfirmationSheetFrame(
      title: strings.confirmTransaction,
      formKey: _formKey,
      confirmedDate: _confirmedDate,
      onDateChanged: (date) => setState(() => _confirmedDate = date),
      onSubmit: () {
        if (!(_formKey.currentState?.validate() ?? false)) {
          return;
        }
        Navigator.pop(
          context,
          SellConfirmationDraft(
            actualReceivedAmount: Decimal.parse(
              _receivedController.text.trim(),
            ),
            confirmedShares: widget.transaction.isPartialSell
                ? Decimal.parse(_sharesController.text.trim())
                : null,
            confirmedDate: _confirmedDate,
          ),
        );
      },
      children: [
        Text('${widget.transaction.fundName} (${widget.transaction.fundCode})'),
        const SizedBox(height: AppSpacing.md),
        TextFormField(
          key: const Key('sell-confirm-received'),
          controller: _receivedController,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            labelText: strings.actualReceivedAmount,
            prefixText: '¥ ',
          ),
          validator: (value) => _decimalValidator(
            value,
            scale: 4,
            error: strings.actualReceivedAmountInvalid,
          ),
        ),
        if (widget.transaction.isPartialSell) ...[
          const SizedBox(height: AppSpacing.md),
          TextFormField(
            key: const Key('sell-confirm-shares'),
            controller: _sharesController,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: strings.confirmedShares,
              hintText: strings.confirmedSharesHint,
            ),
            validator: (value) => _decimalValidator(
              value,
              scale: 8,
              error: strings.confirmedSharesInvalid,
            ),
          ),
        ],
      ],
    );
  }
}

class _ConfirmationSheetFrame extends StatelessWidget {
  const _ConfirmationSheetFrame({
    required this.title,
    required this.formKey,
    required this.confirmedDate,
    required this.onDateChanged,
    required this.onSubmit,
    required this.children,
  });

  final String title;
  final GlobalKey<FormState> formKey;
  final DateTime confirmedDate;
  final ValueChanged<DateTime> onDateChanged;
  final VoidCallback onSubmit;
  final List<Widget> children;

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
          key: formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: AppSpacing.md),
                ...children,
                const SizedBox(height: AppSpacing.md),
                ListTile(
                  key: const Key('transaction-confirm-date'),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.sm,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppRadius.md),
                    side: const BorderSide(color: AppColors.border),
                  ),
                  title: Text(strings.confirmedDate),
                  subtitle: Text(_date(confirmedDate)),
                  trailing: const Icon(Icons.calendar_month_outlined),
                  onTap: () async {
                    final selected = await showDatePicker(
                      context: context,
                      initialDate: confirmedDate,
                      firstDate: DateTime(2000),
                      lastDate: DateTime.now(),
                    );
                    if (selected != null) {
                      onDateChanged(selected);
                    }
                  },
                ),
                const SizedBox(height: AppSpacing.lg),
                FilledButton(
                  key: const Key('transaction-confirm-submit'),
                  onPressed: onSubmit,
                  child: Text(strings.confirm),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CancellationDecision {
  const _CancellationDecision(this.reason);

  final String? reason;
}

class _CancellationDialog extends StatefulWidget {
  const _CancellationDialog();

  @override
  State<_CancellationDialog> createState() => _CancellationDialogState();
}

class _CancellationDialogState extends State<_CancellationDialog> {
  final _reasonController = TextEditingController();

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(strings.cancelTransaction),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(strings.cancelTransactionDescription),
          const SizedBox(height: AppSpacing.md),
          TextField(
            key: const Key('transaction-cancel-reason'),
            controller: _reasonController,
            maxLength: 500,
            decoration: InputDecoration(
              labelText: strings.cancelReason,
              hintText: strings.cancelReasonHint,
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
        ),
        FilledButton(
          key: const Key('transaction-cancel-submit'),
          onPressed: () => Navigator.pop(
            context,
            _CancellationDecision(
              _reasonController.text.trim().isEmpty
                  ? null
                  : _reasonController.text.trim(),
            ),
          ),
          child: Text(strings.confirm),
        ),
      ],
    );
  }
}

class _EmptyTransactions extends StatelessWidget {
  const _EmptyTransactions({required this.strings});

  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            const Icon(
              Icons.receipt_long_outlined,
              size: 44,
              color: AppColors.brandDark,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(strings.transactionListEmpty),
          ],
        ),
      ),
    );
  }
}

class _HistoryError extends StatelessWidget {
  const _HistoryError({
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

String? _decimalValidator(
  String? value, {
  required int scale,
  required String error,
}) {
  final text = value?.trim() ?? '';
  final pattern = RegExp('^\\d+(\\.\\d{1,$scale})?\$');
  if (!pattern.hasMatch(text)) {
    return error;
  }
  return Decimal.parse(text) > Decimal.zero ? null : error;
}

String _statusLabel(AppLocalizations strings, String status) {
  return switch (status) {
    'PENDING' => strings.statusPending,
    'ESTIMATED' => strings.statusEstimated,
    'CONFIRMED' => strings.statusConfirmed,
    'NEEDS_CALIBRATION' => strings.statusNeedsCalibration,
    'CANCELLED' => strings.statusCancelled,
    'REVERSED' => strings.statusReversed,
    _ => status,
  };
}

Color _statusColor(String status) {
  return switch (status) {
    'CONFIRMED' => AppColors.brandDark,
    'PENDING' || 'ESTIMATED' || 'NEEDS_CALIBRATION' => AppColors.warning,
    'CANCELLED' || 'REVERSED' => AppColors.secondaryText,
    _ => AppColors.secondaryText,
  };
}

String _typeLabel(AppLocalizations strings, String type) {
  return switch (type) {
    'BUY' => strings.transactionBuy,
    'SELL' => strings.transactionSell,
    'POSITION_ADJUSTMENT' => strings.transactionAdjustment,
    _ => type,
  };
}

IconData _typeIcon(String type) {
  return switch (type) {
    'BUY' => Icons.add_chart_rounded,
    'SELL' => Icons.trending_down_rounded,
    _ => Icons.tune_rounded,
  };
}

Color _typeColor(String type) {
  return switch (type) {
    'BUY' => AppColors.profit,
    'SELL' => AppColors.loss,
    _ => AppColors.warning,
  };
}

String _periodLabel(AppLocalizations strings, String? period) {
  return switch (period) {
    'BEFORE_15' => strings.before15,
    'AFTER_15' => strings.after15,
    _ => '',
  };
}

String _pendingReason(AppLocalizations strings, String reason) {
  return switch (reason) {
    'OFFICIAL_NAV_UNAVAILABLE' => strings.pendingOfficialNav,
    'FEE_RULE_UNAVAILABLE' => strings.pendingFeeRule,
    'NAV_AND_FEE_UNAVAILABLE' => strings.pendingNavAndFee,
    'SELL_CONFIRMATION_REQUIRED' => strings.pendingSellConfirmation,
    _ => reason,
  };
}

String _date(DateTime? date) {
  if (date == null) {
    return '--';
  }
  String two(int value) => value.toString().padLeft(2, '0');
  return '${date.year}-${two(date.month)}-${two(date.day)}';
}
