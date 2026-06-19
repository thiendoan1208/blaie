package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.domain.AuthConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_identities")
public class AuthIdentityEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_subject", length = 255)
    private String providerSubject;

    @Column(name = "username_normalized", length = 32)
    private String usernameNormalized;

    @Column(name = "email_normalized", length = 255)
    private String emailNormalized;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthIdentityEntity() {
    }

    public static AuthIdentityEntity local(UserEntity user, String usernameNormalized, String emailNormalized, String passwordHash) {
        AuthIdentityEntity identity = new AuthIdentityEntity();
        identity.id = UUID.randomUUID();
        identity.user = user;
        identity.provider = AuthConstants.PROVIDER_LOCAL;
        identity.usernameNormalized = usernameNormalized;
        identity.emailNormalized = emailNormalized;
        identity.emailVerified = false;
        identity.passwordHash = passwordHash;
        return identity;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UserEntity user() {
        return user;
    }

    public String passwordHash() {
        return passwordHash;
    }
}
