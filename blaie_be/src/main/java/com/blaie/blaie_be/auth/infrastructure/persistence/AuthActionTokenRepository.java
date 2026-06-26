package com.blaie.blaie_be.auth.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthActionTokenEntity> findByTokenHashAndType(String tokenHash, String type);

    @Modifying
    @Query("""
            update AuthActionTokenEntity token
               set token.revokedAt = :now,
                   token.revokedReason = :reason
             where token.user.id = :userId
               and token.type = :type
               and token.consumedAt is null
               and token.revokedAt is null
            """)
    void revokeOpenTokens(
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("reason") String reason,
            @Param("now") Instant now
    );
}
