package com.blaie.blaie_be.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "auth_action_tokens")
@EntityListeners(AuditingEntityListener.class)
public class AuthActionTokenEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private Instant createdAt;

    protected AuthActionTokenEntity() {
    }

    public static AuthActionTokenEntity create(UserEntity user, String type, String tokenHash, Instant expiresAt) {
        AuthActionTokenEntity token = new AuthActionTokenEntity();
        token.id = UUID.randomUUID();
        token.user = user;
        token.type = type;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isOpen(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }

    public UserEntity user() {
        return user;
    }
}
