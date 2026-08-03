import 'dart:async';

import 'package:decimal/decimal.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fund_keeper/core/config/app_environment_provider.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/features/entry/presentation/entry_action_sheet.dart';
import 'package:fund_keeper/features/portfolio/application/portfolio_providers.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:fund_keeper/features/portfolio/presentation/portfolio_formatters.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

class PortfolioHomePage extends ConsumerStatefulWidget {
  const PortfolioHomePage({super.key});

  @override
  ConsumerState<PortfolioHomePage> createState() => _PortfolioHomePageState();
}

class _PortfolioHomePageState extends ConsumerState<PortfolioHomePage>
    with WidgetsBindingObserver {
  static const _allAccounts = '__all_accounts__';

  Timer? _pollingTimer;
  FundPrimaryTheme? _selectedTheme;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _startPolling());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.invalidate(portfolioHomeDataProvider);
      _startPolling();
      return;
    }
    _stopPolling();
  }

  @override
  void dispose() {
    _stopPolling();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  void _startPolling() {
    if (!mounted) {
      return;
    }
    _pollingTimer?.cancel();
    final interval = ref.read(appEnvironmentProvider).pollingInterval;
    _pollingTimer = Timer.periodic(interval, (_) {
      if (mounted) {
        ref.invalidate(portfolioHomeDataProvider);
      }
    });
  }

  void _stopPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = null;
  }

  Future<void> _refresh() async {
    ref.invalidate(portfolioAccountsProvider);
    try {
      final _ = await ref.refresh(portfolioHomeDataProvider.future);
    } on Object {
      // The refreshed provider exposes the error state in the page.
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final accounts = ref.watch(portfolioAccountsProvider);
    final home = ref.watch(portfolioHomeDataProvider);

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          key: const Key('portfolio-home-list'),
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
                    strings.appTitle,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                accounts.when(
                  loading: () => const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  error: (_, _) => IconButton(
                    tooltip: strings.accountsLoadFailed,
                    onPressed: () => ref.invalidate(portfolioAccountsProvider),
                    icon: const Icon(Icons.sync_problem_outlined),
                  ),
                  data: (items) => _AccountSelector(
                    value:
                        ref.watch(selectedPortfolioAccountProvider) ??
                        _allAccounts,
                    accounts: items,
                    allAccountsValue: _allAccounts,
                    allAccountsLabel: strings.allAccounts,
                    onChanged: (value) {
                      setState(() => _selectedTheme = null);
                      ref
                          .read(selectedPortfolioAccountProvider.notifier)
                          .select(value == _allAccounts ? null : value);
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            home.when(
              loading: () => const _HomeLoading(),
              error: (error, _) => _HomeError(
                message: error is AppFailure
                    ? error.message
                    : strings.portfolioLoadFailed,
                retryLabel: strings.retry,
                onRetry: () => ref.invalidate(portfolioHomeDataProvider),
              ),
              data: (data) => _buildHome(context, strings, data),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHome(
    BuildContext context,
    AppLocalizations strings,
    PortfolioHomeData data,
  ) {
    final funds = _selectedTheme == null
        ? data.funds
        : data.funds
              .where((fund) => fund.primaryTheme == _selectedTheme)
              .toList(growable: false);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _PortfolioSummaryCard(overview: data.overview, strings: strings),
        const SizedBox(height: AppSpacing.sm),
        _ValuationStatusCard(
          overview: data.overview,
          strings: strings,
          onRefresh: _refresh,
        ),
        const SizedBox(height: AppSpacing.sm),
        _EntryActions(
          strings: strings,
          onManual: _openEntrySheet,
          onJson: () => _openEntry(EntryAction.jsonImport),
        ),
        const SizedBox(height: AppSpacing.sm),
        _ThemeDistributionCard(
          funds: data.funds,
          selectedTheme: _selectedTheme,
          strings: strings,
          onThemeSelected: (theme) {
            setState(() {
              _selectedTheme = _selectedTheme == theme ? null : theme;
            });
          },
          onClear: () => setState(() => _selectedTheme = null),
        ),
        const SizedBox(height: AppSpacing.lg),
        Row(
          children: [
            Expanded(
              child: Text(
                strings.myFunds,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            Text(
              '${funds.length} ${strings.holdingFunds}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        if (data.funds.isEmpty)
          _EmptyPortfolio(strings: strings)
        else if (funds.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.xl),
              child: Text(strings.themeNoData),
            ),
          )
        else
          for (final fund in funds) ...[
            _FundHoldingCard(fund: fund, strings: strings),
            const SizedBox(height: AppSpacing.sm),
          ],
      ],
    );
  }

  Future<void> _openEntry(EntryAction action) async {
    final changed = await switch (action) {
      EntryAction.manualBuy => context.pushNamed<bool>('manual-buy'),
      EntryAction.manualSell => context.pushNamed<bool>('manual-sell'),
      EntryAction.jsonImport => context.pushNamed<bool>('json-import'),
    };
    if (changed == true && mounted) {
      await _refresh();
    }
  }

  Future<void> _openEntrySheet() async {
    final changed = await EntryActionSheet.show(context);
    if (changed && mounted) {
      await _refresh();
    }
  }
}

class _EntryActions extends StatelessWidget {
  const _EntryActions({
    required this.strings,
    required this.onManual,
    required this.onJson,
  });

  final AppLocalizations strings;
  final VoidCallback onManual;
  final VoidCallback onJson;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _EntryActionCard(
            key: const Key('home-json-import'),
            icon: Icons.data_object_rounded,
            title: strings.jsonImport,
            onTap: onJson,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: _EntryActionCard(
            key: const Key('home-manual-entry'),
            icon: Icons.edit_outlined,
            title: strings.manualEntry,
            onTap: onManual,
          ),
        ),
      ],
    );
  }
}

class _EntryActionCard extends StatelessWidget {
  const _EntryActionCard({
    required super.key,
    required this.icon,
    required this.title,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.lg),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: AppColors.brand.withValues(alpha: 0.14),
                foregroundColor: AppColors.brandDark,
                child: Icon(icon),
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AccountSelector extends StatelessWidget {
  const _AccountSelector({
    required this.value,
    required this.accounts,
    required this.allAccountsValue,
    required this.allAccountsLabel,
    required this.onChanged,
  });

  final String value;
  final List<PortfolioAccount> accounts;
  final String allAccountsValue;
  final String allAccountsLabel;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 160),
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
      decoration: BoxDecoration(
        color: AppColors.cardBackground,
        borderRadius: BorderRadius.circular(AppRadius.pill),
        border: Border.all(color: AppColors.border),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          key: const Key('portfolio-account-selector'),
          value: value,
          isExpanded: true,
          borderRadius: BorderRadius.circular(AppRadius.md),
          items: [
            DropdownMenuItem(
              value: allAccountsValue,
              child: Text(allAccountsLabel, overflow: TextOverflow.ellipsis),
            ),
            for (final account in accounts)
              DropdownMenuItem(
                value: account.id,
                child: Text(account.name, overflow: TextOverflow.ellipsis),
              ),
          ],
          onChanged: (selected) {
            if (selected != null) {
              onChanged(selected);
            }
          },
        ),
      ),
    );
  }
}

class _PortfolioSummaryCard extends StatelessWidget {
  const _PortfolioSummaryCard({required this.overview, required this.strings});

  final PortfolioOverview overview;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final marketValue = PortfolioFormatters.money(overview.currentMarketValue);
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.brand, Color(0xFF9BE4D6), AppColors.gradientEnd],
        ),
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: [
          BoxShadow(
            color: AppColors.brand.withValues(alpha: 0.16),
            blurRadius: 18,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            strings.totalAssets,
            style: const TextStyle(color: Colors.white),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            marketValue,
            key: const Key('portfolio-total-assets'),
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
              color: Colors.white,
              fontSize: 34,
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            '${strings.holdingCost} '
            '${PortfolioFormatters.money(overview.totalHoldingCost)}',
            style: TextStyle(color: Colors.white.withValues(alpha: 0.88)),
          ),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: _SummaryMetric(
                  label: strings.cumulativeProfit,
                  value: PortfolioFormatters.money(
                    overview.cumulativeProfit,
                    signed: true,
                  ),
                  valueColor: Colors.white,
                ),
              ),
              Expanded(
                child: _SummaryMetric(
                  label: strings.todayEstimatedProfit,
                  value: PortfolioFormatters.money(
                    overview.todayEstimatedProfit,
                    signed: true,
                  ),
                  valueColor: Colors.white,
                ),
              ),
              Expanded(
                child: _SummaryMetric(
                  label: strings.holdingFunds,
                  value: '${overview.positionCount}',
                  valueColor: Colors.white,
                ),
              ),
            ],
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
    required this.valueColor,
  });

  final String label;
  final String value;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.82),
            fontSize: 12,
          ),
        ),
        const SizedBox(height: AppSpacing.xxs),
        Text(
          value,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: valueColor,
            fontWeight: FontWeight.w700,
            fontSize: 15,
          ),
        ),
      ],
    );
  }
}

class _ValuationStatusCard extends StatelessWidget {
  const _ValuationStatusCard({
    required this.overview,
    required this.strings,
    required this.onRefresh,
  });

  final PortfolioOverview overview;
  final AppLocalizations strings;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    final warning = !overview.valuationComplete
        ? strings.valuationPartial
        : !overview.todayEstimateComplete
        ? strings.todayEstimatePartial
        : overview.containsEstimatedData
        ? strings.containsEstimatedData
        : null;
    final statusColor =
        overview.valuationStatus == PortfolioValuationStatus.live
        ? AppColors.brandDark
        : AppColors.warning;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.show_chart_rounded, color: statusColor),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _valuationStatusLabel(
                      strings,
                      overview.valuationStatus,
                      overview.priceType,
                    ),
                    key: const Key('portfolio-valuation-status'),
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    '${strings.lastUpdated} '
                    '${overview.observedAt == null ? PortfolioFormatters.date(overview.dataDate) : PortfolioFormatters.dateTime(overview.observedAt)}',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  if (warning != null) ...[
                    const SizedBox(height: AppSpacing.xs),
                    Text(
                      warning,
                      key: const Key('portfolio-valuation-warning'),
                      style: const TextStyle(
                        color: AppColors.warning,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            TextButton.icon(
              key: const Key('portfolio-refresh'),
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: Text(strings.refreshValuation),
            ),
          ],
        ),
      ),
    );
  }
}

class _ThemeDistributionCard extends StatelessWidget {
  const _ThemeDistributionCard({
    required this.funds,
    required this.selectedTheme,
    required this.strings,
    required this.onThemeSelected,
    required this.onClear,
  });

  final List<FundPortfolioCard> funds;
  final FundPrimaryTheme? selectedTheme;
  final AppLocalizations strings;
  final ValueChanged<FundPrimaryTheme> onThemeSelected;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final distribution = _themeDistribution(funds);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    strings.themeDistribution,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                if (selectedTheme != null)
                  TextButton(
                    key: const Key('theme-clear-filter'),
                    onPressed: onClear,
                    child: Text(strings.themeAll),
                  ),
              ],
            ),
            Text(
              strings.themeDistributionHint,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.md),
            if (distribution.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
                child: Center(child: Text(strings.themeNoData)),
              )
            else ...[
              SizedBox(
                height: 180,
                child: PieChart(
                  PieChartData(
                    centerSpaceRadius: 42,
                    sectionsSpace: 3,
                    pieTouchData: PieTouchData(
                      touchCallback: (event, response) {
                        if (!event.isInterestedForInteractions) {
                          return;
                        }
                        final index =
                            response?.touchedSection?.touchedSectionIndex;
                        if (index != null &&
                            index >= 0 &&
                            index < distribution.length) {
                          onThemeSelected(distribution[index].theme);
                        }
                      },
                    ),
                    sections: [
                      for (final item in distribution)
                        PieChartSectionData(
                          value: item.amount.toDouble(),
                          color: _themeColor(item.theme),
                          radius: selectedTheme == item.theme ? 57 : 50,
                          showTitle: false,
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: AppSpacing.xs,
                runSpacing: AppSpacing.xs,
                children: [
                  for (final item in distribution)
                    FilterChip(
                      key: Key('theme-${item.theme.name}'),
                      selected: selectedTheme == item.theme,
                      onSelected: (_) => onThemeSelected(item.theme),
                      avatar: CircleAvatar(
                        backgroundColor: _themeColor(item.theme),
                        radius: 5,
                      ),
                      label: Text(
                        '${_themeLabel(strings, item.theme)} '
                        '${PortfolioFormatters.money(item.amount)}',
                      ),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _FundHoldingCard extends StatelessWidget {
  const _FundHoldingCard({required this.fund, required this.strings});

  final FundPortfolioCard fund;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    final returnColor = PortfolioFormatters.valueColor(
      fund.currentHoldingReturnPercent,
    );
    return Card(
      key: Key('fund-card-${fund.fundCode}'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        fund.fundName,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: AppSpacing.xxs),
                      Text(
                        '${fund.fundCode} · '
                        '${_themeLabel(strings, fund.primaryTheme)}',
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
                    color: returnColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(AppRadius.pill),
                  ),
                  child: Text(
                    PortfolioFormatters.percent(
                      fund.currentHoldingReturnPercent,
                      signed: true,
                    ),
                    style: TextStyle(
                      color: returnColor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '${_valuationStatusLabel(strings, fund.valuationStatus, fund.priceType)} · '
              '${fund.observedAt == null ? PortfolioFormatters.date(fund.dataDate) : PortfolioFormatters.dateTime(fund.observedAt)}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const Divider(height: AppSpacing.lg),
            Row(
              children: [
                Expanded(
                  child: _FundMetric(
                    label: strings.holdingAmount,
                    value: PortfolioFormatters.money(fund.currentMarketValue),
                  ),
                ),
                Expanded(
                  child: _FundMetric(
                    label: strings.holdingCost,
                    value: PortfolioFormatters.money(fund.holdingCost),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Expanded(
                  child: _FundMetric(
                    label: strings.holdingProfit,
                    value: PortfolioFormatters.money(
                      fund.currentHoldingProfit,
                      signed: true,
                    ),
                    color: PortfolioFormatters.valueColor(
                      fund.currentHoldingProfit,
                    ),
                  ),
                ),
                Expanded(
                  child: _FundMetric(
                    label: strings.todayProfit,
                    value: PortfolioFormatters.money(
                      fund.todayEstimatedProfit,
                      signed: true,
                    ),
                    color: PortfolioFormatters.valueColor(
                      fund.todayEstimatedProfit,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Row(
              children: [
                Icon(
                  Icons.schedule_outlined,
                  size: 16,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: AppSpacing.xxs),
                Text(
                  '${strings.holdingDays}: '
                  '${fund.holdingDays?.toString() ?? '--'}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                if (fund.openTransactionCount > 0) ...[
                  const SizedBox(width: AppSpacing.md),
                  const Icon(
                    Icons.pending_actions_outlined,
                    size: 16,
                    color: AppColors.warning,
                  ),
                  const SizedBox(width: AppSpacing.xxs),
                  Text(
                    strings.pendingConfirmation,
                    style: const TextStyle(
                      color: AppColors.warning,
                      fontSize: 12,
                    ),
                  ),
                ],
              ],
            ),
            if (fund.pendingBuyAmount != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                '${strings.pendingBuy}: '
                '${PortfolioFormatters.money(fund.pendingBuyAmount)}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _FundMetric extends StatelessWidget {
  const _FundMetric({
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

class _EmptyPortfolio extends StatelessWidget {
  const _EmptyPortfolio({required this.strings});

  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            const Icon(
              Icons.account_balance_wallet_outlined,
              size: 48,
              color: AppColors.brandDark,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              strings.homeFoundationTitle,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              strings.homeFoundationDescription,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class _HomeLoading extends StatelessWidget {
  const _HomeLoading();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      height: 420,
      child: Center(child: CircularProgressIndicator()),
    );
  }
}

class _HomeError extends StatelessWidget {
  const _HomeError({
    required this.message,
    required this.retryLabel,
    required this.onRetry,
  });

  final String message;
  final String retryLabel;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            const Icon(Icons.cloud_off_outlined, size: 42),
            const SizedBox(height: AppSpacing.md),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.md),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded),
              label: Text(retryLabel),
            ),
          ],
        ),
      ),
    );
  }
}

class _ThemeAmount {
  const _ThemeAmount(this.theme, this.amount);

  final FundPrimaryTheme theme;
  final Decimal amount;
}

List<_ThemeAmount> _themeDistribution(List<FundPortfolioCard> funds) {
  final totals = <FundPrimaryTheme, Decimal>{};
  for (final fund in funds) {
    final value = fund.currentMarketValue;
    if (value == null || value <= Decimal.zero) {
      continue;
    }
    totals.update(
      fund.primaryTheme,
      (current) => current + value,
      ifAbsent: () => value,
    );
  }
  final result = totals.entries
      .map((entry) => _ThemeAmount(entry.key, entry.value))
      .toList();
  result.sort((a, b) => b.amount.compareTo(a.amount));
  return result;
}

String _themeLabel(AppLocalizations strings, FundPrimaryTheme theme) {
  return switch (theme) {
    FundPrimaryTheme.semiconductor => strings.themeSemiconductor,
    FundPrimaryTheme.internet => strings.themeInternet,
    FundPrimaryTheme.consumer => strings.themeConsumer,
    FundPrimaryTheme.healthcare => strings.themeHealthcare,
    FundPrimaryTheme.newEnergy => strings.themeNewEnergy,
    FundPrimaryTheme.broadIndex => strings.themeBroadIndex,
    FundPrimaryTheme.finance => strings.themeFinance,
    FundPrimaryTheme.overseas => strings.themeOverseas,
    FundPrimaryTheme.mixed => strings.themeMixed,
    FundPrimaryTheme.other => strings.themeOther,
  };
}

Color _themeColor(FundPrimaryTheme theme) {
  return switch (theme) {
    FundPrimaryTheme.semiconductor => const Color(0xFF42C9B8),
    FundPrimaryTheme.internet => const Color(0xFF67A7F7),
    FundPrimaryTheme.consumer => const Color(0xFFFFB26B),
    FundPrimaryTheme.healthcare => const Color(0xFFF1789F),
    FundPrimaryTheme.newEnergy => const Color(0xFF7ACB85),
    FundPrimaryTheme.broadIndex => const Color(0xFF8B7CF6),
    FundPrimaryTheme.finance => const Color(0xFFE5A94E),
    FundPrimaryTheme.overseas => const Color(0xFF62B6CB),
    FundPrimaryTheme.mixed => const Color(0xFFB58BD2),
    FundPrimaryTheme.other => const Color(0xFF9AA7A5),
  };
}

String _valuationStatusLabel(
  AppLocalizations strings,
  PortfolioValuationStatus? status,
  PortfolioPriceType? priceType,
) {
  if (priceType == PortfolioPriceType.official) {
    return strings.valuationOfficial;
  }
  if (priceType == PortfolioPriceType.mixed &&
      (status == PortfolioValuationStatus.live ||
          status == PortfolioValuationStatus.official ||
          status == PortfolioValuationStatus.marketClosed)) {
    return strings.valuationMixed;
  }
  return switch (status) {
    PortfolioValuationStatus.live => strings.valuationLive,
    PortfolioValuationStatus.delayed => strings.valuationDelayed,
    PortfolioValuationStatus.stale => strings.valuationStale,
    PortfolioValuationStatus.official => strings.valuationOfficial,
    PortfolioValuationStatus.marketClosed => strings.valuationMarketClosed,
    PortfolioValuationStatus.unavailable ||
    null => strings.valuationUnavailable,
  };
}
