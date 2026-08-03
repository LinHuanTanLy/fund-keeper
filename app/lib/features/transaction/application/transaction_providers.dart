import 'package:fund_keeper/core/error/app_failure.dart';
import 'package:fund_keeper/core/network/dio_providers.dart';
import 'package:fund_keeper/features/transaction/data/transaction_remote_data_source.dart';
import 'package:fund_keeper/features/transaction/data/transaction_repository.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'transaction_providers.g.dart';

@Riverpod(keepAlive: true)
TransactionRemoteDataSource transactionRemoteDataSource(Ref ref) {
  return DioTransactionRemoteDataSource(ref.watch(apiDioProvider));
}

@Riverpod(keepAlive: true)
TransactionRepository transactionRepository(Ref ref) {
  return TransactionRepository(ref.watch(transactionRemoteDataSourceProvider));
}

@riverpod
class TransactionHistoryController extends _$TransactionHistoryController {
  static const _pageSize = 20;
  bool _loadingMore = false;

  @override
  Future<TransactionHistoryState> build() {
    return _firstPage(const TransactionFilters());
  }

  Future<void> applyFilters(TransactionFilters filters) async {
    final current = state.value;
    if (current != null) {
      state = AsyncData(
        current.copyWith(
          filters: filters,
          isRefreshing: true,
          clearLoadMoreError: true,
        ),
      );
    }
    state = await AsyncValue.guard(() => _firstPage(filters));
  }

  Future<void> refresh() async {
    final filters = state.value?.filters ?? const TransactionFilters();
    await applyFilters(filters);
  }

  Future<void> loadMore() async {
    final current = state.value;
    if (current == null ||
        current.isRefreshing ||
        current.isLoadingMore ||
        !current.hasNextPage ||
        _loadingMore) {
      return;
    }
    _loadingMore = true;
    state = AsyncData(
      current.copyWith(isLoadingMore: true, clearLoadMoreError: true),
    );
    try {
      final page = await ref
          .read(transactionRepositoryProvider)
          .listTransactions(
            filters: current.filters,
            page: current.page + 1,
            size: _pageSize,
          );
      final records = <String, TransactionRecord>{
        for (final item in current.items) item.id: item,
        for (final item in page.items) item.id: item,
      };
      state = AsyncData(
        TransactionHistoryState(
          filters: current.filters,
          items: records.values.toList(growable: false),
          page: page.page,
          totalPages: page.totalPages,
          totalElements: page.totalElements,
        ),
      );
    } on AppFailure catch (failure) {
      state = AsyncData(
        current.copyWith(isLoadingMore: false, loadMoreError: failure.message),
      );
    } on Object {
      state = AsyncData(
        current.copyWith(isLoadingMore: false, loadMoreError: '加载更多交易失败'),
      );
    } finally {
      _loadingMore = false;
    }
  }

  Future<TransactionHistoryState> _firstPage(TransactionFilters filters) async {
    final page = await ref
        .read(transactionRepositoryProvider)
        .listTransactions(filters: filters, page: 0, size: _pageSize);
    return TransactionHistoryState(
      filters: filters,
      items: page.items,
      page: page.page,
      totalPages: page.totalPages,
      totalElements: page.totalElements,
    );
  }
}
