package com.blaie.blaie_be.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByUsernameNormalized(String usernameNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<UserEntity> findByIdAndStatus(UUID id, String status);

    Optional<UserEntity> findByEmailNormalized(String emailNormalized);

    Optional<UserEntity> findByUsernameNormalized(String usernameNormalized);
}
