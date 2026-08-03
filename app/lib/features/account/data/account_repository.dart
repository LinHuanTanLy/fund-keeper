import 'package:fund_keeper/features/account/data/account_remote_data_source.dart';
import 'package:fund_keeper/features/account/domain/account_models.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

class AccountRepository {
  const AccountRepository(this._remote);

  final AccountRemoteDataSource _remote;

  Future<List<PortfolioAccount>> listAccounts() {
    return _remote.listAccounts(includeArchived: true);
  }

  Future<PortfolioAccount> createAccount(AccountDraft draft) {
    return _remote.createAccount(draft);
  }

  Future<PortfolioAccount> updateAccount(String accountId, AccountDraft draft) {
    return _remote.updateAccount(accountId, draft);
  }

  Future<PortfolioAccount> archiveAccount(String accountId) {
    return _remote.archiveAccount(accountId);
  }
}
