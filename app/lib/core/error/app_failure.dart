sealed class AppFailure implements Exception {
  const AppFailure({required this.message, this.code});

  final String message;
  final String? code;
}

final class NetworkFailure extends AppFailure {
  const NetworkFailure({required super.message, super.code});
}

final class AuthenticationFailure extends AppFailure {
  const AuthenticationFailure({required super.message, super.code});
}

final class PermissionFailure extends AppFailure {
  const PermissionFailure({required super.message, super.code});
}

final class ValidationFailure extends AppFailure {
  const ValidationFailure({
    required super.message,
    this.fieldErrors = const {},
    super.code,
  });

  final Map<String, String> fieldErrors;
}

final class BusinessFailure extends AppFailure {
  const BusinessFailure({required super.message, required super.code});
}

final class ServiceUnavailableFailure extends AppFailure {
  const ServiceUnavailableFailure({required super.message, super.code});
}

final class ProtocolFailure extends AppFailure {
  const ProtocolFailure({required super.message, super.code});
}

final class UnknownFailure extends AppFailure {
  const UnknownFailure({required super.message, super.code});
}
