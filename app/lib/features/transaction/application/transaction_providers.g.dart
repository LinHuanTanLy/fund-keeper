// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'transaction_providers.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(transactionRemoteDataSource)
final transactionRemoteDataSourceProvider =
    TransactionRemoteDataSourceProvider._();

final class TransactionRemoteDataSourceProvider
    extends
        $FunctionalProvider<
          TransactionRemoteDataSource,
          TransactionRemoteDataSource,
          TransactionRemoteDataSource
        >
    with $Provider<TransactionRemoteDataSource> {
  TransactionRemoteDataSourceProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'transactionRemoteDataSourceProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$transactionRemoteDataSourceHash();

  @$internal
  @override
  $ProviderElement<TransactionRemoteDataSource> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  TransactionRemoteDataSource create(Ref ref) {
    return transactionRemoteDataSource(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(TransactionRemoteDataSource value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<TransactionRemoteDataSource>(value),
    );
  }
}

String _$transactionRemoteDataSourceHash() =>
    r'8dbb406a75bb1ba733f39e4924f2ce5e8257c6b7';

@ProviderFor(transactionRepository)
final transactionRepositoryProvider = TransactionRepositoryProvider._();

final class TransactionRepositoryProvider
    extends
        $FunctionalProvider<
          TransactionRepository,
          TransactionRepository,
          TransactionRepository
        >
    with $Provider<TransactionRepository> {
  TransactionRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'transactionRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$transactionRepositoryHash();

  @$internal
  @override
  $ProviderElement<TransactionRepository> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  TransactionRepository create(Ref ref) {
    return transactionRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(TransactionRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<TransactionRepository>(value),
    );
  }
}

String _$transactionRepositoryHash() =>
    r'3e2ea0aed8e561ca9afc2e100ea742476593c0e6';

@ProviderFor(TransactionHistoryController)
final transactionHistoryControllerProvider =
    TransactionHistoryControllerProvider._();

final class TransactionHistoryControllerProvider
    extends
        $AsyncNotifierProvider<
          TransactionHistoryController,
          TransactionHistoryState
        > {
  TransactionHistoryControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'transactionHistoryControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$transactionHistoryControllerHash();

  @$internal
  @override
  TransactionHistoryController create() => TransactionHistoryController();
}

String _$transactionHistoryControllerHash() =>
    r'f6adcbfcfb870cd992a974481c59a1b94ba08fc6';

abstract class _$TransactionHistoryController
    extends $AsyncNotifier<TransactionHistoryState> {
  FutureOr<TransactionHistoryState> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<
              AsyncValue<TransactionHistoryState>,
              TransactionHistoryState
            >;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                AsyncValue<TransactionHistoryState>,
                TransactionHistoryState
              >,
              AsyncValue<TransactionHistoryState>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
