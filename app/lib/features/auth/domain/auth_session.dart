import 'package:json_annotation/json_annotation.dart';

part 'auth_session.g.dart';

enum EmailCodePurpose {
  register('REGISTER'),
  resetPassword('RESET_PASSWORD');

  const EmailCodePurpose(this.wireValue);

  final String wireValue;
}

@JsonSerializable()
class AuthUser {
  const AuthUser({required this.id, required this.email});

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return _$AuthUserFromJson(json);
  }

  final String id;
  final String email;

  Map<String, dynamic> toJson() => _$AuthUserToJson(this);
}

@JsonSerializable(explicitToJson: true)
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.accessTokenExpiresAt,
    required this.refreshTokenExpiresAt,
    required this.user,
  });

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return _$AuthSessionFromJson(json);
  }

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final DateTime accessTokenExpiresAt;
  final DateTime refreshTokenExpiresAt;
  final AuthUser user;

  bool accessTokenNeedsRefresh(DateTime now) {
    return !accessTokenExpiresAt.isAfter(now.add(const Duration(seconds: 30)));
  }

  bool refreshTokenIsExpired(DateTime now) {
    return !refreshTokenExpiresAt.isAfter(now);
  }

  AuthSession copyWith({AuthUser? user}) {
    return AuthSession(
      accessToken: accessToken,
      refreshToken: refreshToken,
      tokenType: tokenType,
      accessTokenExpiresAt: accessTokenExpiresAt,
      refreshTokenExpiresAt: refreshTokenExpiresAt,
      user: user ?? this.user,
    );
  }

  Map<String, dynamic> toJson() => _$AuthSessionToJson(this);
}
