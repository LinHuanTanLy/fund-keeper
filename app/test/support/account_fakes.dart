import 'package:fund_keeper/features/account/data/account_remote_data_source.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

class FakeAccountRemoteDataSource implements AccountRemoteDataSource {
  FakeAccountRemoteDataSource({List<PortfolioAccount>? accounts})
    : accounts = accounts ?? [];

  List<PortfolioAccount> accounts;
  Object? listFailure;
  Object? createFailure;
  Object? updateFailure;
  Object? archiveFailure;
  int listCalls = 0;
  int createCalls = 0;
  int updateCalls = 0;
  int archiveCalls = 0;
  bool? lastIncludeArchived;
  AccountDraft? lastCreateDraft;
  AccountDraft? lastUpdateDraft;
  String? lastUpdateId;
  String? lastArchiveId;

  @override
  Future<List<PortfolioAccount>> listAccounts({
    required bool includeArchived,
  }) async {
    listCalls += 1;
    lastIncludeArchived = includeArchived;
    final failure = listFailure;
    listFailure = null;
    if (failure != null) {
      throw failure;
    }
    return accounts
        .where((account) => includeArchived || account.status == 'ACTIVE')
        .toList(growable: false);
  }

  @override
  Future<PortfolioAccount> createAccount(AccountDraft draft) async {
    createCalls += 1;
    lastCreateDraft = draft;
    final failure = createFailure;
    createFailure = null;
    if (failure != null) {
      throw failure;
    }
    final created = accountFixture(
      id: 'account-created-$createCalls',
      name: draft.name,
      platform: draft.platform.apiValue,
    );
    accounts = [...accounts, created];
    return created;
  }

  @override
  Future<PortfolioAccount> updateAccount(
    String accountId,
    AccountDraft draft,
  ) async {
    updateCalls += 1;
    lastUpdateId = accountId;
    lastUpdateDraft = draft;
    final failure = updateFailure;
    updateFailure = null;
    if (failure != null) {
      throw failure;
    }
    final updated = accountFixture(
      id: accountId,
      name: draft.name,
      platform: draft.platform.apiValue,
    );
    _replace(updated);
    return updated;
  }

  @override
  Future<PortfolioAccount> archiveAccount(String accountId) async {
    archiveCalls += 1;
    lastArchiveId = accountId;
    final failure = archiveFailure;
    archiveFailure = null;
    if (failure != null) {
      throw failure;
    }
    final old = accounts.firstWhere((account) => account.id == accountId);
    final archived = accountFixture(
      id: old.id,
      name: old.name,
      platform: old.platform,
      status: 'ARCHIVED',
    );
    _replace(archived);
    return archived;
  }

  void _replace(PortfolioAccount replacement) {
    accounts = [
      for (final account in accounts)
        if (account.id == replacement.id) replacement else account,
    ];
  }
}

PortfolioAccount accountFixture({
  required String id,
  required String name,
  String platform = 'OTHER',
  String status = 'ACTIVE',
}) {
  return PortfolioAccount(
    id: id,
    name: name,
    platform: platform,
    status: status,
  );
}
