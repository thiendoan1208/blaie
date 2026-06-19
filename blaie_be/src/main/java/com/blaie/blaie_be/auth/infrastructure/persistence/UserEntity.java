package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.api.response.AuthUserResponse;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;

    @Column(name = "username", length = 32)
    private String username;

    @Column(name = "username_normalized", length = 32)
    private String usernameNormalized;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "email_normalized", length = 255)
    private String emailNormalized;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public static UserEntity localUser(String username, String usernameNormalized, String email, String emailNormalized, String displayName) {
        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.username = username;
        user.usernameNormalized = usernameNormalized;
        user.email = email;
        user.emailNormalized = emailNormalized;
        user.displayName = displayName;
        user.status = AuthConstants.USER_STATUS_ACTIVE;
        user.admin = false;
        return user;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null || status.isBlank()) {
            status = AuthConstants.USER_STATUS_ACTIVE;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public AuthUserResponse toAuthUserResponse() {
        return new AuthUserResponse(id, username, email, displayName, avatarUrl, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String status() {
        return status;
    }

    public boolean admin() {
        return admin;
    }
}
