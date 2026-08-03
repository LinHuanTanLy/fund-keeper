import 'package:fund_keeper/core/network/dio_providers.dart';
import 'package:fund_keeper/features/account/data/account_remote_data_source.dart';
import 'package:fund_keeper/features/account/data/account_repository.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'account_providers.g.dart';

@Riverpod(keepAlive: true)
AccountRemoteDataSource accountRemoteDataSource(Ref ref) {
  return DioAccountRemoteDataSource(ref.watch(apiDioProvider));
}

@Riverpod(keepAlive: true)
AccountRepository accountRepository(Ref ref) {
  return AccountRepository(ref.watch(accountRemoteDataSourceProvider));
}

@riverpod
class AccountManagementController extends _$AccountManagementController {
  @override
  Future<List<PortfolioAccount>> build() {
    return ref.watch(accountRepositoryProvider).listAccounts();
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(
      () => ref.read(accountRepositoryProvider).listAccounts(),
    );
  }

  Future<PortfolioAccount> createAccount(AccountDraft draft) async {
    final created = await ref
        .read(accountRepositoryProvider)
        .createAccount(draft);
    final current = state.value ?? const <PortfolioAccount>[];
    state = AsyncData([...current, created]);
    return created;
  }

  Future<PortfolioAccount> updateAccount(
    String accountId,
    AccountDraft draft,
  ) async {
    final updated = await ref
        .read(accountRepositoryProvider)
        .updateAccount(accountId, draft);
    _replace(updated);
    return updated;
  }

  Future<PortfolioAccount> archiveAccount(String accountId) async {
    final archived = await ref
        .read(accountRepositoryProvider)
        .archiveAccount(accountId);
    _replace(archived);
    return archived;
  }

  void _replace(PortfolioAccount account) {
    final current = state.value ?? const <PortfolioAccount>[];
    state = AsyncData([
      for (final item in current)
        if (item.id == account.id) account else item,
    ]);
  }
}
