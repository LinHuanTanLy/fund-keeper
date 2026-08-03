// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'portfolio_providers.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(portfolioRemoteDataSource)
final portfolioRemoteDataSourceProvider = PortfolioRemoteDataSourceProvider._();

final class PortfolioRemoteDataSourceProvider
    extends
        $FunctionalProvider<
          PortfolioRemoteDataSource,
          PortfolioRemoteDataSource,
          PortfolioRemoteDataSource
        >
    with $Provider<PortfolioRemoteDataSource> {
  PortfolioRemoteDataSourceProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'portfolioRemoteDataSourceProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$portfolioRemoteDataSourceHash();

  @$internal
  @override
  $ProviderElement<PortfolioRemoteDataSource> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  PortfolioRemoteDataSource create(Ref ref) {
    return portfolioRemoteDataSource(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(PortfolioRemoteDataSource value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<PortfolioRemoteDataSource>(value),
    );
  }
}

String _$portfolioRemoteDataSourceHash() =>
    r'7d3574a53b26606c836f95a237b7792543833c3d';

@ProviderFor(portfolioRepository)
final portfolioRepositoryProvider = PortfolioRepositoryProvider._();

final class PortfolioRepositoryProvider
    extends
        $FunctionalProvider<
          PortfolioRepository,
          PortfolioRepository,
          PortfolioRepository
        >
    with $Provider<PortfolioRepository> {
  PortfolioRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'portfolioRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$portfolioRepositoryHash();

  @$internal
  @override
  $ProviderElement<PortfolioRepository> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  PortfolioRepository create(Ref ref) {
    return portfolioRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(PortfolioRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<PortfolioRepository>(value),
    );
  }
}

String _$portfolioRepositoryHash() =>
    r'066490ca2f781839521b9b63f9334a4503f1ff11';

@ProviderFor(portfolioAccounts)
final portfolioAccountsProvider = PortfolioAccountsProvider._();

final class PortfolioAccountsProvider
    extends
        $FunctionalProvider<
          AsyncValue<List<PortfolioAccount>>,
          List<PortfolioAccount>,
          FutureOr<List<PortfolioAccount>>
        >
    with
        $FutureModifier<List<PortfolioAccount>>,
        $FutureProvider<List<PortfolioAccount>> {
  PortfolioAccountsProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'portfolioAccountsProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$portfolioAccountsHash();

  @$internal
  @override
  $FutureProviderElement<List<PortfolioAccount>> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<List<PortfolioAccount>> create(Ref ref) {
    return portfolioAccounts(ref);
  }
}

String _$portfolioAccountsHash() => r'7436f233d8f593d0207436013b8fcb4fb60f45e9';

@ProviderFor(portfolioFunds)
final portfolioFundsProvider = PortfolioFundsFamily._();

final class PortfolioFundsProvider
    extends
        $FunctionalProvider<
          AsyncValue<List<FundPortfolioCard>>,
          List<FundPortfolioCard>,
          FutureOr<List<FundPortfolioCard>>
        >
    with
        $FutureModifier<List<FundPortfolioCard>>,
        $FutureProvider<List<FundPortfolioCard>> {
  PortfolioFundsProvider._({
    required PortfolioFundsFamily super.from,
    required String super.argument,
  }) : super(
         retry: null,
         name: r'portfolioFundsProvider',
         isAutoDispose: true,
         dependencies: null,
         $allTransitiveDependencies: null,
       );

  @override
  String debugGetCreateSourceHash() => _$portfolioFundsHash();

  @override
  String toString() {
    return r'portfolioFundsProvider'
        ''
        '($argument)';
  }

  @$internal
  @override
  $FutureProviderElement<List<FundPortfolioCard>> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<List<FundPortfolioCard>> create(Ref ref) {
    final argument = this.argument as String;
    return portfolioFunds(ref, argument);
  }

  @override
  bool operator ==(Object other) {
    return other is PortfolioFundsProvider && other.argument == argument;
  }

  @override
  int get hashCode {
    return argument.hashCode;
  }
}

String _$portfolioFundsHash() => r'2ec275b6513111eecd484cde8d6c632ca64e2631';

final class PortfolioFundsFamily extends $Family
    with $FunctionalFamilyOverride<FutureOr<List<FundPortfolioCard>>, String> {
  PortfolioFundsFamily._()
    : super(
        retry: null,
        name: r'portfolioFundsProvider',
        dependencies: null,
        $allTransitiveDependencies: null,
        isAutoDispose: true,
      );

  PortfolioFundsProvider call(String accountId) =>
      PortfolioFundsProvider._(argument: accountId, from: this);

  @override
  String toString() => r'portfolioFundsProvider';
}

@ProviderFor(SelectedPortfolioAccount)
final selectedPortfolioAccountProvider = SelectedPortfolioAccountProvider._();

final class SelectedPortfolioAccountProvider
    extends $NotifierProvider<SelectedPortfolioAccount, String?> {
  SelectedPortfolioAccountProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'selectedPortfolioAccountProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$selectedPortfolioAccountHash();

  @$internal
  @override
  SelectedPortfolioAccount create() => SelectedPortfolioAccount();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(String? value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<String?>(value),
    );
  }
}

String _$selectedPortfolioAccountHash() =>
    r'5f1eead5e903f53ade0d173ad85f29a5a113142c';

abstract class _$SelectedPortfolioAccount extends $Notifier<String?> {
  String? build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<String?, String?>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<String?, String?>,
              String?,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

@ProviderFor(portfolioHomeData)
final portfolioHomeDataProvider = PortfolioHomeDataProvider._();

final class PortfolioHomeDataProvider
    extends
        $FunctionalProvider<
          AsyncValue<PortfolioHomeData>,
          PortfolioHomeData,
          FutureOr<PortfolioHomeData>
        >
    with
        $FutureModifier<PortfolioHomeData>,
        $FutureProvider<PortfolioHomeData> {
  PortfolioHomeDataProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'portfolioHomeDataProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$portfolioHomeDataHash();

  @$internal
  @override
  $FutureProviderElement<PortfolioHomeData> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<PortfolioHomeData> create(Ref ref) {
    return portfolioHomeData(ref);
  }
}

String _$portfolioHomeDataHash() => r'5a8a2df78fdcdc98bcfa840a92ecfdadb1c81e8c';
