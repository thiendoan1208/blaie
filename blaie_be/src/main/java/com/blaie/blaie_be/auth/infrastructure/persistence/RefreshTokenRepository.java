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

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshTokenEntity r set r.revokedAt = :now, r.revokedReason = :reason " +
           "where r.tokenFamilyId = :familyId and r.revokedAt is null")
    void revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshTokenEntity r set r.revokedAt = :now, r.revokedReason = :reason " +
           "where r.user.id = :userId and r.revokedAt is null")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("reason") String reason, @Param("now") Instant now);
}
