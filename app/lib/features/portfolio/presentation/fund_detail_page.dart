import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_formatters.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';

class FundDetailPage extends ConsumerWidget {
  const FundDetailPage({required this.fundCode, super.key});

  final String fundCode;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = AppLocalizations.of(context);
    final detail = ref.watch(fundPortfolioDetailProvider(fundCode));
    return Scaffold(
      appBar: AppBar(title: Text(strings.fundDetailTitle)),
      body: SafeArea(
        child: detail.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _LoadError(
            message: error is AppFailure
                ? error.message
                : strings.fundDetailLoadFailed,
            retryLabel: strings.retry,
            onRetry: () =>
                ref.invalidate(fundPortfolioDetailProvider(fundCode)),
          ),
          data: (value) => RefreshIndicator(
            onRefresh: () =>
                ref.refresh(fundPortfolioDetailProvider(fundCode).future),
            child: _FundDetailContent(detail: value, strings: strings),
          ),
        ),
      ),
    );
  }
}

class _FundDetailContent extends StatelessWidget {
  const _FundDetailContent({required this.detail, required this.strings});

  final FundPortfolioDetail detail;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    return ListView(
      key: const Key('fund-detail-list'),
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.md,
        AppSpacing.md,
        AppSpacing.md,
        AppSpacing.xl,
      ),
      children: [
        _SummaryCard(summary: detail.summary, strings: strings),
        const SizedBox(height: AppSpacing.lg),
        _SectionTitle(
          title: strings.accountPositions,
          count: detail.accounts.length,
        ),
        const SizedBox(height: AppSpacing.sm),
        for (final account in detail.accounts) ...[
          _AccountCard(account: account, strings: strings),
          const SizedBox(height: AppSpacing.sm),
        ],
        const SizedBox(height: AppSpacing.sm),
        _SectionTitle(
          title: strings.openTransactions,
          count: detail.openTransactions.length,
        ),
        const SizedBox(height: AppSpacing.sm),
        if (detail.openTransactions.isEmpty)
          _EmptyCard(message: strings.noOpenTransactions)
        else
          for (final item in detail.openTransactions) ...[
            _TransactionCard(transaction: item, strings: strings),
            const SizedBox(height: AppSpacing.sm),
          ],
        const SizedBox(height: AppSpacing.lg),
        _SectionTitle(
          title: strings.recentTransactions,
          count: detail.transactions.totalElements,
        ),
        const SizedBox(height: AppSpacing.sm),
        if (detail.transactions.items.isEmpty)
          _EmptyCard(message: strings.noRecentTransactions)
        else
          for (final item in detail.transactions.items) ...[
            _TransactionCard(transaction: item, strings: strings),
            const SizedBox(height: AppSpacing.sm),
          ],
      ],
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.summary, required this.strings});

  final FundPortfolioCard summary;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final profitColor = PortfolioFormatters.valueColor(
      summary.currentHoldingProfit,
    );
    return Container(
      key: const Key('fund-detail-summary'),
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.brand, Color(0xFF92E2D7), AppColors.gradientEnd],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(AppRadius.lg),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            summary.fundName,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.xxs),
          Text(summary.fundCode, style: const TextStyle(color: Colors.white70)),
          const SizedBox(height: AppSpacing.lg),
          Text(
            strings.holdingAmount,
            style: const TextStyle(color: Colors.white70),
          ),
          const SizedBox(height: AppSpacing.xxs),
          Text(
            PortfolioFormatters.money(summary.currentMarketValue),
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              Expanded(
                child: _SummaryMetric(
                  label: strings.holdingProfit,
                  value: PortfolioFormatters.money(
                    summary.currentHoldingProfit,
                    signed: true,
                  ),
                  color: profitColor,
                ),
              ),
              Expanded(
                child: _SummaryMetric(
                  label: strings.cumulativeProfit,
                  value: PortfolioFormatters.money(
                    summary.cumulativeProfit,
                    signed: true,
                  ),
                  color: PortfolioFormatters.valueColor(
                    summary.cumulativeProfit,
                  ),
                ),
              ),
              Expanded(
                child: _SummaryMetric(
                  label: strings.todayProfit,
                  value: PortfolioFormatters.money(
                    summary.todayEstimatedProfit,
                    signed: true,
                  ),
                  color: PortfolioFormatters.valueColor(
                    summary.todayEstimatedProfit,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            '${_valuationLabel(strings, summary.valuationStatus, summary.priceType)} · '
            '${summary.observedAt == null ? PortfolioFormatters.date(summary.dataDate) : PortfolioFormatters.dateTime(summary.observedAt)}',
            style: const TextStyle(color: Colors.white),
          ),
        ],
      ),
    );
  }
}

class _SummaryMetric extends StatelessWidget {
  const _SummaryMetric({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 12),
        ),
        const SizedBox(height: AppSpacing.xxs),
        Text(
          value,
          style: TextStyle(color: color, fontWeight: FontWeight.w700),
        ),
      ],
    );
  }
}

class _AccountCard extends StatelessWidget {
  const _AccountCard({required this.account, required this.strings});

  final FundPortfolioAccount account;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    return Card(
      key: Key('fund-detail-account-${account.accountId}'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    account.accountName,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                Text(
                  PortfolioFormatters.percent(
                    account.currentHoldingReturnPercent,
                    signed: true,
                  ),
                  style: TextStyle(
                    color: PortfolioFormatters.valueColor(
                      account.currentHoldingReturnPercent,
                    ),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              '${_platformLabel(strings, account.accountPlatform)} · '
              '${_valuationLabel(strings, account.valuationStatus, account.priceType)}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const Divider(height: AppSpacing.lg),
            _MetricGrid(
              items: [
                (strings.holdingAmount, account.currentMarketValue, false),
                (strings.holdingCost, account.holdingCost, false),
                (strings.holdingProfit, account.currentHoldingProfit, true),
                (strings.realizedProfit, account.realizedProfit, true),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              spacing: AppSpacing.md,
              runSpacing: AppSpacing.xs,
              children: [
                Text(
                  '${strings.fundShares}: ${_decimal(account.shares)}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                Text(
                  '${strings.currentPrice}: ${_decimal(account.unitNav)}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                Text(
                  '${strings.holdingDays}: ${account.holdingDays ?? '--'}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
            if (account.dataSource != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                '${strings.dataSource}: ${account.dataSource}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _MetricGrid extends StatelessWidget {
  const _MetricGrid({required this.items});

  final List<(String, Decimal?, bool)> items;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      runSpacing: AppSpacing.md,
      children: [
        for (final item in items)
          SizedBox(
            width: MediaQuery.sizeOf(context).width / 2 - AppSpacing.xl,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.$1, style: Theme.of(context).textTheme.bodyMedium),
                const SizedBox(height: AppSpacing.xxs),
                Text(
                  PortfolioFormatters.money(item.$2, signed: item.$3),
                  style: TextStyle(
                    color: item.$3
                        ? PortfolioFormatters.valueColor(item.$2)
                        : AppColors.primaryText,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _TransactionCard extends StatelessWidget {
  const _TransactionCard({required this.transaction, required this.strings});

  final TransactionRecord transaction;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final amount = transaction.isSell
        ? transaction.actualReceivedAmount ?? transaction.expectedAmount
        : transaction.amount;
    return Card(
      key: Key('fund-detail-transaction-${transaction.id}'),
      child: ListTile(
        title: Text(
          '${transaction.isBuy ? strings.transactionBuy : strings.transactionSell} · '
          '${_statusLabel(strings, transaction.status)}',
        ),
        subtitle: Text(
          '${transaction.accountName} · '
          '${PortfolioFormatters.date(transaction.submittedDate)}',
        ),
        trailing: Text(
          PortfolioFormatters.money(amount, signed: transaction.isSell),
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, required this.count});

  final String title;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(title, style: Theme.of(context).textTheme.titleLarge),
        ),
        Text('$count', style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _EmptyCard extends StatelessWidget {
  const _EmptyCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Center(child: Text(message)),
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
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.md),
            FilledButton(onPressed: onRetry, child: Text(retryLabel)),
          ],
        ),
      ),
    );
  }
}

String _decimal(Decimal? value) => value?.toString() ?? '--';

String _platformLabel(AppLocalizations strings, String platform) {
  return switch (platform) {
    'ALIPAY' => strings.platformAlipay,
    'TIANTIAN_FUND' => strings.platformTiantianFund,
    'BANK' => strings.platformBank,
    _ => strings.platformOther,
  };
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

String _valuationLabel(
  AppLocalizations strings,
  PortfolioValuationStatus? status,
  PortfolioPriceType? priceType,
) {
  if (priceType == PortfolioPriceType.mixed) {
    return strings.valuationMixed;
  }
  return switch (status) {
    PortfolioValuationStatus.live => strings.valuationLive,
    PortfolioValuationStatus.delayed => strings.valuationDelayed,
    PortfolioValuationStatus.stale => strings.valuationStale,
    PortfolioValuationStatus.official => strings.valuationOfficial,
    PortfolioValuationStatus.marketClosed => strings.valuationMarketClosed,
    _ => strings.valuationUnavailable,
  };
}
