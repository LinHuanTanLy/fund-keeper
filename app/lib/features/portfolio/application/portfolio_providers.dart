import 'package:fund_keeper/core/network/dio_providers.dart';
import 'package:fund_keeper/features/portfolio/data/portfolio_remote_data_source.dart';
import 'package:fund_keeper/features/portfolio/data/portfolio_repository.dart';
import 'package:fund_keeper/features/portfolio/domain/portfolio_models.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'portfolio_providers.g.dart';

@Riverpod(keepAlive: true)
PortfolioRemoteDataSource portfolioRemoteDataSource(Ref ref) {
  return DioPortfolioRemoteDataSource(ref.watch(apiDioProvider));
}

@Riverpod(keepAlive: true)
PortfolioRepository portfolioRepository(Ref ref) {
  return PortfolioRepository(ref.watch(portfolioRemoteDataSourceProvider));
}

@riverpod
Future<List<PortfolioAccount>> portfolioAccounts(Ref ref) {
  return ref.watch(portfolioRepositoryProvider).listAccounts();
}

@riverpod
Future<List<FundPortfolioCard>> portfolioFunds(Ref ref, String accountId) {
  return ref.watch(portfolioRepositoryProvider).listFunds(accountId);
}

@riverpod
class SelectedPortfolioAccount extends _$SelectedPortfolioAccount {
  @override
  String? build() => null;

  void select(String? accountId) {
    state = accountId;
  }
}

@riverpod
Future<PortfolioHomeData> portfolioHomeData(Ref ref) {
  final accountId = ref.watch(selectedPortfolioAccountProvider);
  return ref.watch(portfolioRepositoryProvider).loadHome(accountId);
}

@riverpod
Future<FundPortfolioDetail> fundPortfolioDetail(Ref ref, String fundCode) {
  return ref.watch(portfolioRepositoryProvider).getFundDetail(fundCode);
}
