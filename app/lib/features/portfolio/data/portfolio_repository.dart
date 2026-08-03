import 'package:fund_keeper/features/portfolio/data/portfolio_remote_data_source.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';

class PortfolioRepository {
  const PortfolioRepository(this._remote);

  final PortfolioRemoteDataSource _remote;

  Future<List<PortfolioAccount>> listAccounts() {
    return _remote.listAccounts();
  }

  Future<List<FundPortfolioCard>> listFunds(String accountId) {
    return _remote.listFunds(accountId);
  }

  Future<PortfolioHomeData> loadHome(String? accountId) async {
    final overviewFuture = _remote.getOverview(accountId);
    final fundsFuture = _remote.listFunds(accountId);
    final (overview, funds) = await (overviewFuture, fundsFuture).wait;
    return PortfolioHomeData(overview: overview, funds: funds);
  }
}
