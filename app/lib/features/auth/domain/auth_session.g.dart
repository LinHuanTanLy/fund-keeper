// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'auth_session.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AuthUser _$AuthUserFromJson(Map<String, dynamic> json) =>
    AuthUser(id: json['id'] as String, email: json['email'] as String);

Map<String, dynamic> _$AuthUserToJson(AuthUser instance) => <String, dynamic>{
  'id': instance.id,
  'email': instance.email,
};

AuthSession _$AuthSessionFromJson(Map<String, dynamic> json) => AuthSession(
  accessToken: json['accessToken'] as String,
  refreshToken: json['refreshToken'] as String,
  tokenType: json['tokenType'] as String,
  accessTokenExpiresAt: DateTime.parse(json['accessTokenExpiresAt'] as String),
  refreshTokenExpiresAt: DateTime.parse(
    json['refreshTokenExpiresAt'] as String,
  ),
  user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
);

Map<String, dynamic> _$AuthSessionToJson(AuthSession instance) =>
    <String, dynamic>{
      'accessToken': instance.accessToken,
      'refreshToken': instance.refreshToken,
      'tokenType': instance.tokenType,
      'accessTokenExpiresAt': instance.accessTokenExpiresAt.toIso8601String(),
      'refreshTokenExpiresAt': instance.refreshTokenExpiresAt.toIso8601String(),
      'user': instance.user.toJson(),
    };
