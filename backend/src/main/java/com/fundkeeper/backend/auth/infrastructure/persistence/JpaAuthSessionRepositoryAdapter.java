package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.auth.domain.AuthSession;
import com.fundkeeper.backend.auth.domain.AuthSessionRepository;

@Repository
public class JpaAuthSessionRepositoryAdapter implements AuthSessionRepository {

    private final SpringDataAuthSessionRepository repository;

    JpaAuthSessionRepositoryAdapter(SpringDataAuthSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AuthSession> findByRefreshTokenHashForUpdate(String refreshTokenHash) {
        return repository.findByRefreshTokenHash(refreshTokenHash)
                .map(AuthSessionJpaEntity::toDomain);
    }

    @Override
    public Optional<AuthSession> findByPublicId(String publicId) {
        return repository.findByPublicId(publicId)
                .map(AuthSessionJpaEntity::toDomain);
    }

    @Override
    public AuthSession save(AuthSession session) {
        if (session.id() == null) {
            return repository.save(AuthSessionJpaEntity.fromDomain(session)).toDomain();
        }
        AuthSessionJpaEntity entity = repository.findById(session.id())
                .orElseThrow(() -> new IllegalStateException("Auth session no longer exists"));
        entity.apply(session);
        return repository.save(entity).toDomain();
    }

    @Override
    public void revokeAllByUserId(long userId, Instant revokedAt) {
        repository.revokeAllByUserId(userId, revokedAt);
    }
}
