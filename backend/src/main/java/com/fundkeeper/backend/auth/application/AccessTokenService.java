package com.fundkeeper.backend.auth.application;

import java.time.Instant;

import com.fundkeeper.backend.auth.domain.AuthSession;
import com.fundkeeper.backend.auth.domain.User;

public interface AccessTokenService {

    IssuedAccessToken issue(User user, AuthSession session);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
