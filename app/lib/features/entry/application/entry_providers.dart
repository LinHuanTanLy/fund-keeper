import 'package:fund_keeper/core/network/dio_providers.dart';
import 'package:fund_keeper/features/entry/data/entry_remote_data_source.dart';
import 'package:fund_keeper/features/entry/data/entry_repository.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'entry_providers.g.dart';

@Riverpod(keepAlive: true)
EntryRemoteDataSource entryRemoteDataSource(Ref ref) {
  return DioEntryRemoteDataSource(ref.watch(apiDioProvider));
}

@Riverpod(keepAlive: true)
EntryRepository entryRepository(Ref ref) {
  return EntryRepository(ref.watch(entryRemoteDataSourceProvider));
}
