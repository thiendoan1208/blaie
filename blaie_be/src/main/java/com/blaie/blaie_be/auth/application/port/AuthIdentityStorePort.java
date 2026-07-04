package com.blaie.blaie_be.auth.application.port;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityStorePort {
    void createLocalIdentity(UUID userId, String passwordHash);

    void createGoogleIdentity(UUID userId, String providerSubject);

    Optional<AuthIdentityView> findSingleLocalIdentityByIdentifier(String identifierNormalized);

    Optional<AuthUserView> findGoogleUserBySubject(String providerSubject);

    Optional<AuthIdentityView> findLocalIdentity(UUID userId);

    boolean existsVerifiedEmail(UUID userId);

    void markLocalEmailVerified(UUID userId);

    void updateLocalPasswordHash(UUID userId, String passwordHash);
}
