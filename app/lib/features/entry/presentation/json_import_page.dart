import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/entry/application/entry_providers.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/transaction/application/transaction_providers.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

class JsonImportPage extends ConsumerStatefulWidget {
  const JsonImportPage({super.key});

  @override
  ConsumerState<JsonImportPage> createState() => _JsonImportPageState();
}

class _JsonImportPageState extends ConsumerState<JsonImportPage> {
  final _jsonController = TextEditingController();
  ImportKind _kind = ImportKind.positionSnapshot;
  ImportPreflightResult? _preflight;
  ImportCommitResult? _commitResult;
  bool _preflighting = false;
  bool _committing = false;
  bool _commitMayBeRetried = false;
  bool _mustRepreflight = false;
  String? _error;

  @override
  void dispose() {
    _jsonController.dispose();
    super.dispose();
  }

  void _contentChanged() {
    if (_preflight == null && _error == null) {
      return;
    }
    setState(() {
      _preflight = null;
      _commitResult = null;
      _commitMayBeRetried = false;
      _mustRepreflight = false;
      _error = null;
    });
  }

  Future<void> _preflightImport() async {
    if (_preflighting || _committing) {
      return;
    }
    final raw = _jsonController.text.trim();
    if (raw.isEmpty) {
      setState(() => _error = AppLocalizations.of(context).jsonRequired);
      return;
    }
    setState(() {
      _preflighting = true;
      _error = null;
      _preflight = null;
      _commitResult = null;
      _commitMayBeRetried = false;
      _mustRepreflight = false;
    });
    try {
      final result = await ref
          .read(entryRepositoryProvider)
          .preflightImport(_kind, raw);
      if (mounted) {
        setState(() => _preflight = result);
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        setState(() => _error = failure.message);
      }
    } on Object {
      if (mounted) {
        setState(() => _error = AppLocalizations.of(context).importFailed);
      }
    } finally {
      if (mounted) {
        setState(() => _preflighting = false);
      }
    }
  }

  Future<void> _commitImport() async {
    final preflight = _preflight;
    if (_committing ||
        preflight == null ||
        !preflight.canCommit ||
        _mustRepreflight) {
      return;
    }
    setState(() {
      _committing = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(entryRepositoryProvider)
          .commitImport(_kind, preflight.batchId!);
      if (!mounted) {
        return;
      }
      ref
        ..invalidate(portfolioAccountsProvider)
        ..invalidate(portfolioHomeDataProvider)
        ..invalidate(transactionHistoryControllerProvider);
      setState(() {
        _commitResult = result;
        _commitMayBeRetried = false;
      });
    } on NetworkFailure catch (failure) {
      if (mounted) {
        setState(() {
          _error = failure.message;
          _commitMayBeRetried = true;
        });
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        setState(() {
          _error = failure.message;
          _mustRepreflight = true;
          _commitMayBeRetried = false;
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _error = AppLocalizations.of(context).importFailed;
          _mustRepreflight = true;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _committing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final commitResult = _commitResult;
    if (commitResult != null) {
      return _ImportSuccess(
        result: commitResult,
        strings: strings,
        onComplete: () => context.pop(true),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(strings.jsonImportTitle)),
      body: SafeArea(
        child: ListView(
          key: const Key('json-import-list'),
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.md,
            AppSpacing.md,
            AppSpacing.md,
            AppSpacing.xl,
          ),
          children: [
            Text(
              strings.jsonImportDescription,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.lg),
            Text(
              strings.importKind,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: AppSpacing.xs),
            SegmentedButton<ImportKind>(
              key: const Key('json-import-kind'),
              segments: [
                ButtonSegment(
                  value: ImportKind.positionSnapshot,
                  label: Text(strings.positionSnapshot),
                  icon: const Icon(Icons.account_balance_wallet_outlined),
                ),
                ButtonSegment(
                  value: ImportKind.transactionBatch,
                  label: Text(strings.transactionBatch),
                  icon: const Icon(Icons.receipt_long_outlined),
                ),
              ],
              selected: {_kind},
              onSelectionChanged: (selection) {
                setState(() {
                  _kind = selection.single;
                  _preflight = null;
                  _commitResult = null;
                  _error = null;
                  _mustRepreflight = false;
                  _commitMayBeRetried = false;
                });
              },
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              _kind == ImportKind.positionSnapshot
                  ? strings.positionSnapshotHint
                  : strings.transactionBatchHint,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              key: const Key('json-import-content'),
              controller: _jsonController,
              minLines: 12,
              maxLines: 20,
              autocorrect: false,
              enableSuggestions: false,
              style: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 13,
                height: 1.4,
              ),
              decoration: InputDecoration(
                alignLabelWithHint: true,
                labelText: strings.jsonContent,
                hintText: strings.jsonContentHint,
              ),
              onChanged: (_) => _contentChanged(),
            ),
            if (_error != null && _preflight == null) ...[
              const SizedBox(height: AppSpacing.md),
              _ImportMessage(
                message: _mustRepreflight
                    ? '${_error!}\n${strings.repreflightRequired}'
                    : _error!,
                isError: true,
              ),
            ],
            const SizedBox(height: AppSpacing.md),
            FilledButton.icon(
              key: const Key('json-import-preflight'),
              onPressed: _preflighting || _committing ? null : _preflightImport,
              icon: const Icon(Icons.fact_check_outlined),
              label: Text(
                _preflighting ? strings.preflighting : strings.preflight,
              ),
            ),
            if (_preflight != null) ...[
              const SizedBox(height: AppSpacing.lg),
              _PreflightResultCard(
                result: _preflight!,
                strings: strings,
                committing: _committing,
                commitMayBeRetried: _commitMayBeRetried,
                mustRepreflight: _mustRepreflight,
                error: _error,
                onCommit: _commitImport,
                onComplete: _completeExistingImport,
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _completeExistingImport() {
    ref
      ..invalidate(portfolioAccountsProvider)
      ..invalidate(portfolioHomeDataProvider)
      ..invalidate(transactionHistoryControllerProvider);
    context.pop(true);
  }
}

class _PreflightResultCard extends StatelessWidget {
  const _PreflightResultCard({
    required this.result,
    required this.strings,
    required this.committing,
    required this.commitMayBeRetried,
    required this.mustRepreflight,
    required this.error,
    required this.onCommit,
    required this.onComplete,
  });

  final ImportPreflightResult result;
  final AppLocalizations strings;
  final bool committing;
  final bool commitMayBeRetried;
  final bool mustRepreflight;
  final String? error;
  final VoidCallback onCommit;
  final VoidCallback onComplete;

  @override
  Widget build(BuildContext context) {
    final statusMessage = result.isCommitted
        ? strings.alreadyCommitted
        : result.canCommit
        ? strings.readyToCommit
        : strings.preflightFailed;
    final statusColor = result.canCommit || result.isCommitted
        ? AppColors.brandDark
        : AppColors.profit;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              strings.preflightResult,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              statusMessage,
              key: const Key('json-import-preflight-status'),
              style: TextStyle(color: statusColor, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: AppSpacing.sm),
            if (result.batchId != null)
              _SummaryLine(label: strings.batchId, value: result.batchId!),
            if (result.accountName != null)
              _SummaryLine(
                label: strings.platformAccount,
                value:
                    '${result.accountName}'
                    '${result.accountWillCreate ? ' · ${strings.willCreateAccount}' : ''}',
              ),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.xs,
              children: [
                _CountChip(label: strings.totalRows, count: result.totalCount),
                _CountChip(
                  label: strings.importableRows,
                  count: result.importableCount,
                ),
                _CountChip(
                  label: strings.warnings,
                  count: result.warningCount,
                  color: AppColors.warning,
                ),
                _CountChip(
                  label: strings.errors,
                  count: result.errorCount,
                  color: AppColors.profit,
                ),
                if (result.calibrationCount > 0)
                  _CountChip(
                    label: strings.calibrations,
                    count: result.calibrationCount,
                    color: AppColors.warning,
                  ),
              ],
            ),
            if (result.issues.isNotEmpty) ...[
              const Divider(height: AppSpacing.lg),
              for (final issue in result.issues) _IssueLine(issue: issue),
            ],
            if (result.calibrationCount > 0) ...[
              const SizedBox(height: AppSpacing.md),
              _ImportMessage(
                message:
                    '${strings.calibrationReviewTitle}\n'
                    '${strings.calibrationReviewHint}',
                isError: false,
                color: AppColors.warning,
              ),
            ],
            if (result.rows.isNotEmpty) ...[
              const Divider(height: AppSpacing.lg),
              SizedBox(
                height: result.calibrationCount > 0 ? 480 : 340,
                child: ListView.separated(
                  key: const Key('json-import-rows'),
                  itemCount: result.rows.length,
                  separatorBuilder: (_, _) =>
                      const Divider(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    return _RowPreview(
                      row: result.rows[index],
                      strings: strings,
                    );
                  },
                ),
              ),
            ],
            if (error != null) ...[
              const SizedBox(height: AppSpacing.md),
              _ImportMessage(
                message: mustRepreflight
                    ? '$error\n${strings.repreflightRequired}'
                    : error!,
                isError: true,
              ),
            ],
            if (result.isCommitted) ...[
              const SizedBox(height: AppSpacing.md),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: onComplete,
                  child: Text(strings.complete),
                ),
              ),
            ] else if (result.canCommit) ...[
              const SizedBox(height: AppSpacing.md),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  key: const Key('json-import-commit'),
                  onPressed: committing || mustRepreflight ? null : onCommit,
                  icon: const Icon(Icons.cloud_upload_outlined),
                  label: Text(
                    committing
                        ? strings.committing
                        : commitMayBeRetried
                        ? strings.retryCommit
                        : strings.confirmImport,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RowPreview extends StatelessWidget {
  const _RowPreview({required this.row, required this.strings});

  final ImportRowPreview row;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final titleParts = [
      strings.rowNumber(row.row),
      if (row.fundCode != null) row.fundCode!,
      if (row.fundName != null) row.fundName!,
    ];
    return Padding(
      key: Key('json-import-row-${row.row}'),
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            titleParts.join(' · '),
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          if (row.action != null || row.resultStatus != null) ...[
            const SizedBox(height: AppSpacing.xxs),
            Text(
              [row.action, row.resultStatus].whereType<String>().join(' · '),
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
          if (row.needsCalibration) ...[
            const SizedBox(height: AppSpacing.sm),
            _CalibrationReview(row: row, strings: strings),
          ],
          for (final issue in row.issues) _IssueLine(issue: issue),
        ],
      ),
    );
  }
}

class _CalibrationReview extends StatelessWidget {
  const _CalibrationReview({required this.row, required this.strings});

  final ImportRowPreview row;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final current = row.currentPosition;
    final target = row.targetPosition;
    final difference = row.difference;
    return Container(
      key: Key('json-import-calibration-${row.row}'),
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: AppColors.warning.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.warning.withValues(alpha: 0.45)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.compare_arrows_rounded,
                size: 18,
                color: AppColors.warning,
              ),
              const SizedBox(width: AppSpacing.xxs),
              Expanded(
                child: Text(
                  row.clearsPosition
                      ? strings.positionWillClear
                      : strings.calibrationWillApply,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          _ComparisonHeader(strings: strings),
          _ComparisonLine(
            label: strings.fundShares,
            current: _shares(current?.shares),
            target: _shares(target?.shares),
          ),
          _ComparisonLine(
            label: strings.holdingCost,
            current: _money(current?.costAmount),
            target: _money(target?.costAmount),
          ),
          _ComparisonLine(
            label: strings.transactionStatus,
            current: _positionStatus(current?.status, strings),
            target: _positionStatus(target?.status, strings),
          ),
          _ComparisonLine(
            label: strings.holdingStartDate,
            current: _date(current?.holdingStartDate),
            target: _date(target?.holdingStartDate),
          ),
          if (difference != null) ...[
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.xs,
              runSpacing: AppSpacing.xs,
              children: [
                if (difference.sharesDelta != null)
                  _DifferenceChip(
                    label:
                        '${strings.sharesDelta} '
                        '${_signed(difference.sharesDelta!)}',
                  ),
                if (difference.costAmountDelta != null)
                  _DifferenceChip(
                    label:
                        '${strings.costDelta} '
                        '${_signedMoney(difference.costAmountDelta!)}',
                  ),
                if (difference.statusChanged)
                  _DifferenceChip(label: strings.statusWillChange),
                if (difference.holdingStartDateChanged)
                  _DifferenceChip(label: strings.holdingStartDateWillChange),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _ComparisonHeader extends StatelessWidget {
  const _ComparisonHeader({required this.strings});

  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(
      context,
    ).textTheme.labelSmall?.copyWith(color: AppColors.secondaryText);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.xxs),
      child: Row(
        children: [
          const SizedBox(width: 76),
          Expanded(child: Text(strings.currentValue, style: style)),
          const SizedBox(width: AppSpacing.xs),
          Expanded(child: Text(strings.targetValue, style: style)),
        ],
      ),
    );
  }
}

class _ComparisonLine extends StatelessWidget {
  const _ComparisonLine({
    required this.label,
    required this.current,
    required this.target,
  });

  final String label;
  final String current;
  final String target;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.xxs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 76,
            child: Text(label, style: Theme.of(context).textTheme.bodySmall),
          ),
          Expanded(child: Text(current)),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: AppSpacing.xxs),
            child: Icon(Icons.arrow_forward_rounded, size: 14),
          ),
          Expanded(child: Text(target)),
        ],
      ),
    );
  }
}

class _DifferenceChip extends StatelessWidget {
  const _DifferenceChip({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xs,
        vertical: AppSpacing.xxs,
      ),
      decoration: BoxDecoration(
        color: AppColors.warning.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AppRadius.pill),
      ),
      child: Text(label, style: Theme.of(context).textTheme.labelSmall),
    );
  }
}

class _IssueLine extends StatelessWidget {
  const _IssueLine({required this.issue});

  final ImportIssuePreview issue;

  @override
  Widget build(BuildContext context) {
    final color = issue.isError ? AppColors.profit : AppColors.warning;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xxs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            issue.isError
                ? Icons.error_outline_rounded
                : Icons.warning_amber_rounded,
            size: 16,
            color: color,
          ),
          const SizedBox(width: AppSpacing.xxs),
          Expanded(
            child: Text(
              '${issue.field == null ? '' : '${issue.field}: '}'
              '${issue.message} (${issue.code})',
              style: TextStyle(color: color, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}

class _CountChip extends StatelessWidget {
  const _CountChip({
    required this.label,
    required this.count,
    this.color = AppColors.brandDark,
  });

  final String label;
  final int count;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Chip(
      label: Text('$label $count'),
      side: BorderSide(color: color.withValues(alpha: 0.3)),
      backgroundColor: color.withValues(alpha: 0.08),
      labelStyle: TextStyle(color: color),
    );
  }
}

class _SummaryLine extends StatelessWidget {
  const _SummaryLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.xs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 88, child: Text(label)),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

class _ImportMessage extends StatelessWidget {
  const _ImportMessage({
    required this.message,
    required this.isError,
    this.color,
  });

  final String message;
  final bool isError;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final resolvedColor =
        color ?? (isError ? AppColors.profit : AppColors.brandDark);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: BoxDecoration(
        color: resolvedColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: resolvedColor.withValues(alpha: 0.3)),
      ),
      child: Text(message, style: TextStyle(color: resolvedColor)),
    );
  }
}

String _shares(Decimal? value) => value?.toString() ?? '--';

String _money(Decimal? value) =>
    value == null ? '--' : '¥${value.toStringAsFixed(2)}';

String _signed(Decimal value) =>
    '${value > Decimal.zero ? '+' : ''}${value.toString()}';

String _signedMoney(Decimal value) =>
    '${value > Decimal.zero
        ? '+'
        : value < Decimal.zero
        ? '-'
        : ''}'
    '¥${(value < Decimal.zero ? -value : value).toStringAsFixed(2)}';

String _date(DateTime? value) {
  if (value == null) {
    return '--';
  }
  String two(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${two(value.month)}-${two(value.day)}';
}

String _positionStatus(String? status, AppLocalizations strings) {
  return switch (status) {
    'CONFIRMED' => strings.statusConfirmed,
    'ESTIMATED' => strings.statusEstimated,
    'PENDING' => strings.statusPending,
    'NEEDS_CALIBRATION' => strings.statusNeedsCalibration,
    null => strings.emptyPosition,
    _ => status,
  };
}

class _ImportSuccess extends StatelessWidget {
  const _ImportSuccess({
    required this.result,
    required this.strings,
    required this.onComplete,
  });

  final ImportCommitResult result;
  final AppLocalizations strings;
  final VoidCallback onComplete;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: Text(strings.jsonImportTitle),
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
                strings.importSuccess,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppSpacing.lg),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Column(
                    children: [
                      _SummaryLine(
                        label: strings.batchId,
                        value: result.batchId,
                      ),
                      _SummaryLine(
                        label: strings.appliedRows,
                        value: '${result.appliedCount}',
                      ),
                      _SummaryLine(
                        label: strings.platformAccount,
                        value: result.accountCreated
                            ? strings.accountCreated
                            : result.accountId,
                      ),
                    ],
                  ),
                ),
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  key: const Key('json-import-complete'),
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
