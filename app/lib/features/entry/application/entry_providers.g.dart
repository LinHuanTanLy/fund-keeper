// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'entry_providers.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(entryRemoteDataSource)
final entryRemoteDataSourceProvider = EntryRemoteDataSourceProvider._();

final class EntryRemoteDataSourceProvider
    extends
        $FunctionalProvider<
          EntryRemoteDataSource,
          EntryRemoteDataSource,
          EntryRemoteDataSource
        >
    with $Provider<EntryRemoteDataSource> {
  EntryRemoteDataSourceProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'entryRemoteDataSourceProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$entryRemoteDataSourceHash();

  @$internal
  @override
  $ProviderElement<EntryRemoteDataSource> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  EntryRemoteDataSource create(Ref ref) {
    return entryRemoteDataSource(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(EntryRemoteDataSource value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<EntryRemoteDataSource>(value),
    );
  }
}

String _$entryRemoteDataSourceHash() =>
    r'f6a10f4c014ccb476b630b37c44cd70024f3419b';

@ProviderFor(entryRepository)
final entryRepositoryProvider = EntryRepositoryProvider._();

final class EntryRepositoryProvider
    extends
        $FunctionalProvider<EntryRepository, EntryRepository, EntryRepository>
    with $Provider<EntryRepository> {
  EntryRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'entryRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$entryRepositoryHash();

  @$internal
  @override
  $ProviderElement<EntryRepository> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  EntryRepository create(Ref ref) {
    return entryRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(EntryRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<EntryRepository>(value),
    );
  }
}

String _$entryRepositoryHash() => r'7bdbe967222570ae72b46e8b24b26e4f6c8554f0';
