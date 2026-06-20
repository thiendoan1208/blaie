package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.domain.AuthConstants;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "auth_identities")
@EntityListeners(AuditingEntityListener.class)
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

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    protected AuthIdentityEntity() {
    }

    public static AuthIdentityEntity local(UserEntity user, String passwordHash) {
        AuthIdentityEntity identity = new AuthIdentityEntity();
        identity.id = UUID.randomUUID();
        identity.user = user;
        identity.provider = AuthConstants.PROVIDER_LOCAL;
        identity.emailVerified = false;
        identity.passwordHash = passwordHash;
        return identity;
    }

    public UserEntity user() {
        return user;
    }

    public String passwordHash() {
        return passwordHash;
    }
}
