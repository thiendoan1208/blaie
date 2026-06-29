package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.domain.AuthConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
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
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
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

    public static UserEntity googleUser(String email, String emailNormalized, String displayName, String avatarUrl) {
        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.email = email;
        user.emailNormalized = emailNormalized;
        user.displayName = displayName;
        user.avatarUrl = avatarUrl;
        user.status = AuthConstants.USER_STATUS_ACTIVE;
        user.admin = false;
        return user;
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String status() {
        return status;
    }

    public boolean admin() {
        return admin;
    }

    public void updateUsername(String username, String usernameNormalized) {
        this.username = username;
        this.usernameNormalized = usernameNormalized;
    }
}
