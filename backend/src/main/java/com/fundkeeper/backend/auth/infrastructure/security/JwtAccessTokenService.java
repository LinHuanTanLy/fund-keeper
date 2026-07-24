package com.fundkeeper.backend.auth.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.fundkeeper.backend.auth.application.AccessTokenService;
import com.fundkeeper.backend.auth.application.AuthProperties;
import com.fundkeeper.backend.auth.domain.AuthSession;
import com.fundkeeper.backend.auth.domain.User;

@Service
public class JwtAccessTokenService implements AccessTokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtAccessTokenService(
            JwtEncoder encoder,
            AuthProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(User user, AuthSession session) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.publicId())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", session.publicId())
                .claim("ver", user.tokenVersion())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}
