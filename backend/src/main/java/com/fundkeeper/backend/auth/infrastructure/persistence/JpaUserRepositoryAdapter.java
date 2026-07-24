package com.fundkeeper.backend.auth.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.auth.domain.User;
import com.fundkeeper.backend.auth.domain.UserRepository;

@Repository
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    JpaUserRepositoryAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return repository.existsByEmail(normalizedEmail);
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return repository.findByEmail(normalizedEmail)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return repository.findById(id)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByPublicId(String publicId) {
        return repository.findByPublicId(publicId)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByPublicIdForUpdate(String publicId) {
        return repository.findByPublicIdForUpdate(publicId)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public User save(User user) {
        if (user.id() == null) {
            return repository.save(UserJpaEntity.fromDomain(user)).toDomain();
        }
        UserJpaEntity entity = repository.findById(user.id())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));
        entity.apply(user);
        return repository.save(entity).toDomain();
    }
}
