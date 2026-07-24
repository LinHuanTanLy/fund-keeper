package com.fundkeeper.backend.auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository {

    Optional<AuthSession> findByRefreshTokenHashForUpdate(String refreshTokenHash);

    Optional<AuthSession> findByPublicId(String publicId);

    AuthSession save(AuthSession session);

    void revokeAllByUserId(long userId, Instant revokedAt);
}
