package com.blaie.blaie_be.auth.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentityEntity, UUID> {
    boolean existsByProviderAndUsernameNormalized(String provider, String usernameNormalized);

    boolean existsByProviderAndEmailNormalized(String provider, String emailNormalized);

    @Query("""
            select identity
            from AuthIdentityEntity identity
            join fetch identity.user
            where identity.provider = :provider
              and (identity.usernameNormalized = :identifier or identity.emailNormalized = :identifier)
            """)
    List<AuthIdentityEntity> findAllByProviderAndIdentifier(
            @Param("provider") String provider,
            @Param("identifier") String identifier
    );
}
