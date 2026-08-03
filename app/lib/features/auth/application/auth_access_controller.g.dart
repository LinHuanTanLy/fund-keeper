// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'auth_access_controller.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(AuthAccessController)
final authAccessControllerProvider = AuthAccessControllerFamily._();

final class AuthAccessControllerProvider
    extends $NotifierProvider<AuthAccessController, AuthAccessState> {
  AuthAccessControllerProvider._({
    required AuthAccessControllerFamily super.from,
    required EmailCodePurpose super.argument,
  }) : super(
         retry: null,
         name: r'authAccessControllerProvider',
         isAutoDispose: true,
         dependencies: null,
         $allTransitiveDependencies: null,
       );

  @override
  String debugGetCreateSourceHash() => _$authAccessControllerHash();

  @override
  String toString() {
    return r'authAccessControllerProvider'
        ''
        '($argument)';
  }

  @$internal
  @override
  AuthAccessController create() => AuthAccessController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AuthAccessState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AuthAccessState>(value),
    );
  }

  @override
  bool operator ==(Object other) {
    return other is AuthAccessControllerProvider && other.argument == argument;
  }

  @override
  int get hashCode {
    return argument.hashCode;
  }
}

String _$authAccessControllerHash() =>
    r'fe3609465ef9908113d9fa646dc931a306810920';

final class AuthAccessControllerFamily extends $Family
    with
        $ClassFamilyOverride<
          AuthAccessController,
          AuthAccessState,
          AuthAccessState,
          AuthAccessState,
          EmailCodePurpose
        > {
  AuthAccessControllerFamily._()
    : super(
        retry: null,
        name: r'authAccessControllerProvider',
        dependencies: null,
        $allTransitiveDependencies: null,
        isAutoDispose: true,
      );

  AuthAccessControllerProvider call(EmailCodePurpose purpose) =>
      AuthAccessControllerProvider._(argument: purpose, from: this);

  @override
  String toString() => r'authAccessControllerProvider';
}

abstract class _$AuthAccessController extends $Notifier<AuthAccessState> {
  late final _$args = ref.$arg as EmailCodePurpose;
  EmailCodePurpose get purpose => _$args;

  AuthAccessState build(EmailCodePurpose purpose);
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AuthAccessState, AuthAccessState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AuthAccessState, AuthAccessState>,
              AuthAccessState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, () => build(_$args));
  }
}
