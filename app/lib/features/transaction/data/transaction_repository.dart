import 'package:fund_keeper/features/transaction/data/transaction_remote_data_source.dart';
import 'package:fund_keeper/features/transaction/domain/transaction_models.dart';

class TransactionRepository {
  const TransactionRepository(this._remote);

  final TransactionRemoteDataSource _remote;

  Future<TransactionPage> listTransactions({
    required TransactionFilters filters,
    required int page,
    int size = 20,
  }) {
    return _remote.listTransactions(filters: filters, page: page, size: size);
  }

  Future<TransactionRecord> confirmBuy(
    String transactionId,
    BuyConfirmationDraft draft,
  ) {
    return _remote.confirmBuy(transactionId, draft);
  }

  Future<TransactionRecord> confirmSell(
    String transactionId,
    SellConfirmationDraft draft,
  ) {
    return _remote.confirmSell(transactionId, draft);
  }

  Future<TransactionRecord> cancel(String transactionId, String? reason) {
    return _remote.cancel(transactionId, reason);
  }
}
