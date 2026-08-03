import 'dart:math';

import 'package:fund_keeper/features/entry/data/entry_remote_data_source.dart';
import 'package:fund_keeper/features/entry/domain/entry_models.dart';

class EntryRepository {
  EntryRepository(this._remote, {Random? random})
    : _random = random ?? Random.secure();

  final EntryRemoteDataSource _remote;
  final Random _random;

  String createBuyRequestId() => _createRequestId('buy');

  String createSellRequestId() => _createRequestId('sell');

  String _createRequestId(String prefix) {
    final timestamp = DateTime.now().microsecondsSinceEpoch;
    final suffix = _random.nextInt(0x7fffffff).toRadixString(16);
    return '$prefix-$timestamp-$suffix';
  }

  Future<ManualBuyResult> createManualBuy(ManualBuyDraft draft) {
    return _remote.createManualBuy(draft);
  }

  Future<ManualSellResult> createManualSell(ManualSellDraft draft) {
    return _remote.createManualSell(draft);
  }

  Future<ImportPreflightResult> preflightImport(
    ImportKind kind,
    String rawJson,
  ) {
    return _remote.preflightImport(kind, rawJson);
  }

  Future<ImportCommitResult> commitImport(ImportKind kind, String batchId) {
    return _remote.commitImport(kind, batchId);
  }
}
