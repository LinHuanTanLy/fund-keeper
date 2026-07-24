package com.fundkeeper.backend.auth.domain;

import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String normalizedEmail);

    Optional<User> findByEmail(String normalizedEmail);

    Optional<User> findById(long id);

    Optional<User> findByPublicId(String publicId);

    Optional<User> findByPublicIdForUpdate(String publicId);

    User save(User user);
}
