// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'account_providers.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(accountRemoteDataSource)
final accountRemoteDataSourceProvider = AccountRemoteDataSourceProvider._();

final class AccountRemoteDataSourceProvider
    extends
        $FunctionalProvider<
          AccountRemoteDataSource,
          AccountRemoteDataSource,
          AccountRemoteDataSource
        >
    with $Provider<AccountRemoteDataSource> {
  AccountRemoteDataSourceProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'accountRemoteDataSourceProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$accountRemoteDataSourceHash();

  @$internal
  @override
  $ProviderElement<AccountRemoteDataSource> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  AccountRemoteDataSource create(Ref ref) {
    return accountRemoteDataSource(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AccountRemoteDataSource value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AccountRemoteDataSource>(value),
    );
  }
}

String _$accountRemoteDataSourceHash() =>
    r'f9bfc5f23cf6f7ee0cfffa3d59fb3b3136b7b8e8';

@ProviderFor(accountRepository)
final accountRepositoryProvider = AccountRepositoryProvider._();

final class AccountRepositoryProvider
    extends
        $FunctionalProvider<
          AccountRepository,
          AccountRepository,
          AccountRepository
        >
    with $Provider<AccountRepository> {
  AccountRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'accountRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$accountRepositoryHash();

  @$internal
  @override
  $ProviderElement<AccountRepository> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  AccountRepository create(Ref ref) {
    return accountRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AccountRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AccountRepository>(value),
    );
  }
}

String _$accountRepositoryHash() => r'21ed5f5a96ec21995da4724ced42b773bec6f313';

@ProviderFor(AccountManagementController)
final accountManagementControllerProvider =
    AccountManagementControllerProvider._();

final class AccountManagementControllerProvider
    extends
        $AsyncNotifierProvider<
          AccountManagementController,
          List<PortfolioAccount>
        > {
  AccountManagementControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'accountManagementControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$accountManagementControllerHash();

  @$internal
  @override
  AccountManagementController create() => AccountManagementController();
}

String _$accountManagementControllerHash() =>
    r'872fe74549e8134aea9b385cf679c5e2f438ebef';

abstract class _$AccountManagementController
    extends $AsyncNotifier<List<PortfolioAccount>> {
  FutureOr<List<PortfolioAccount>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<AsyncValue<List<PortfolioAccount>>, List<PortfolioAccount>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                AsyncValue<List<PortfolioAccount>>,
                List<PortfolioAccount>
              >,
              AsyncValue<List<PortfolioAccount>>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
