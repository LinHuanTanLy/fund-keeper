import 'package:flutter/material.dart';
import 'package:fund_keeper/core/design_system/app_colors.dart';
import 'package:fund_keeper/core/design_system/app_spacing.dart';
import 'package:fund_keeper/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';

enum EntryAction { manualBuy, manualSell, jsonImport }

abstract final class EntryActionSheet {
  static Future<bool> show(BuildContext context) async {
    final strings = AppLocalizations.of(context);
    final action = await showModalBottomSheet<EntryAction>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.md,
              0,
              AppSpacing.md,
              AppSpacing.lg,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  strings.chooseEntryMethod,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: AppSpacing.md),
                _ActionTile(
                  icon: Icons.edit_outlined,
                  title: strings.manualBuyTitle,
                  description: strings.manualBuyDescription,
                  onTap: () => Navigator.pop(context, EntryAction.manualBuy),
                ),
                const SizedBox(height: AppSpacing.sm),
                _ActionTile(
                  icon: Icons.trending_down_rounded,
                  title: strings.manualSellTitle,
                  description: strings.manualSellDescription,
                  onTap: () => Navigator.pop(context, EntryAction.manualSell),
                ),
                const SizedBox(height: AppSpacing.sm),
                _ActionTile(
                  icon: Icons.data_object_rounded,
                  title: strings.jsonImportTitle,
                  description: strings.jsonImportDescription,
                  onTap: () => Navigator.pop(context, EntryAction.jsonImport),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    if (action == null || !context.mounted) {
      return false;
    }
    final result = await switch (action) {
      EntryAction.manualBuy => context.pushNamed<bool>('manual-buy'),
      EntryAction.manualSell => context.pushNamed<bool>('manual-sell'),
      EntryAction.jsonImport => context.pushNamed<bool>('json-import'),
    };
    return result ?? false;
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.cardBackground,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.lg),
        side: const BorderSide(color: AppColors.border),
      ),
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.all(AppSpacing.sm),
        leading: CircleAvatar(
          backgroundColor: AppColors.brand.withValues(alpha: 0.14),
          foregroundColor: AppColors.brandDark,
          child: Icon(icon),
        ),
        title: Text(title),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: AppSpacing.xxs),
          child: Text(description),
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
      ),
    );
  }
}
