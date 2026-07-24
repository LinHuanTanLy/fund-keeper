package com.fundkeeper.backend.auth.infrastructure.security;

import java.time.Clock;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fundkeeper.backend.auth.domain.AuthSessionRepository;
import com.fundkeeper.backend.auth.domain.UserRepository;

final class SessionJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR =
            new OAuth2Error("invalid_token", "Authentication session is no longer active", null);

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final Clock clock;

    SessionJwtValidator(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String userPublicId = token.getSubject();
        String sessionPublicId = token.getClaimAsString("sid");
        Number tokenVersion = token.getClaim("ver");
        if (userPublicId == null || sessionPublicId == null || tokenVersion == null) {
            return OAuth2TokenValidatorResult.failure(ERROR);
        }

        var user = userRepository.findByPublicId(userPublicId);
        var session = sessionRepository.findByPublicId(sessionPublicId);
        boolean valid = user.isPresent()
                && user.get().isActive()
                && user.get().tokenVersion() == tokenVersion.longValue()
                && session.isPresent()
                && session.get().userId() == user.get().id()
                && session.get().isActiveAt(clock.instant());

        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERROR);
    }
}
