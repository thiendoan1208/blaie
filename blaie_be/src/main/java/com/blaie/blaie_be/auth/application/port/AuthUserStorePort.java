package com.blaie.blaie_be.auth.application.port;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserStorePort {
    boolean existsByUsernameNormalized(String usernameNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    AuthUserView createLocalUser(String username, String usernameNormalized, String email, String emailNormalized, String displayName);

    AuthUserView createGoogleUser(String email, String emailNormalized, String displayName, String avatarUrl);

    Optional<AuthUserView> findByEmailNormalized(String emailNormalized);

    Optional<AuthUserView> findActiveById(UUID userId);

    Optional<AuthUserView> findByUsernameNormalized(String usernameNormalized);

    void updateUsername(UUID userId, String username, String usernameNormalized);
}
