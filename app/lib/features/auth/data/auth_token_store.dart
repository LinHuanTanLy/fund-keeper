import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:fund_keeper/features/auth/domain/auth_session.dart';

abstract interface class AuthTokenStore {
  Future<AuthSession?> read();

  Future<void> write(AuthSession session);

  Future<void> clear();
}

class SecureAuthTokenStore implements AuthTokenStore {
  SecureAuthTokenStore({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _sessionKey = 'fund_keeper.auth_session.v1';

  final FlutterSecureStorage _storage;

  @override
  Future<AuthSession?> read() async {
    final encoded = await _storage.read(key: _sessionKey);
    if (encoded == null) {
      return null;
    }

    try {
      return AuthSession.fromJson(jsonDecode(encoded) as Map<String, dynamic>);
    } on Object {
      await clear();
      return null;
    }
  }

  @override
  Future<void> write(AuthSession session) {
    return _storage.write(
      key: _sessionKey,
      value: jsonEncode(session.toJson()),
    );
  }

  @override
  Future<void> clear() => _storage.delete(key: _sessionKey);
}
