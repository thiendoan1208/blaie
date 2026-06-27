package com.blaie.blaie_be.auth.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentityEntity, UUID> {
    @Query("""
            select identity
            from AuthIdentityEntity identity
            join fetch identity.user
            where identity.provider = :provider
              and (
                  identity.user.usernameNormalized = :identifier
                  or identity.user.emailNormalized = :identifier
              )
            """)
    List<AuthIdentityEntity> findAllByProviderAndIdentifier(
            @Param("provider") String provider,
            @Param("identifier") String identifier
    );

    Optional<AuthIdentityEntity> findByUser_IdAndProvider(UUID userId, String provider);

    Optional<AuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);

    boolean existsByUser_IdAndEmailVerifiedTrue(UUID userId);
}
