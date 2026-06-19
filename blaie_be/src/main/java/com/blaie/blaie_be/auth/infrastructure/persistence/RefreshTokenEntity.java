package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.domain.AuthConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType;

    @Column(name = "token_transport", nullable = false, length = 20)
    private String tokenTransport;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    private RefreshTokenEntity replacedByToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshTokenEntity() {
    }

    public static RefreshTokenEntity webCookie(UserEntity user, String tokenHash, UUID tokenFamilyId, Instant expiresAt, String userAgent) {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.id = UUID.randomUUID();
        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.tokenFamilyId = tokenFamilyId;
        refreshToken.clientType = AuthConstants.CLIENT_TYPE_WEB;
        refreshToken.tokenTransport = AuthConstants.TOKEN_TRANSPORT_COOKIE;
        refreshToken.expiresAt = expiresAt;
        refreshToken.userAgent = blankToNull(userAgent);
        return refreshToken;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        lastUsedAt = now;
    }

    public void revoke(String reason, RefreshTokenEntity replacement, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
            replacedByToken = replacement;
        }
    }

    public UserEntity user() {
        return user;
    }

    public UUID tokenFamilyId() {
        return tokenFamilyId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
