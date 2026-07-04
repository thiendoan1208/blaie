package com.blaie.blaie_be.auth.application.port;

import java.util.UUID;

public interface AuthTokenPort {
    String generateRefreshToken();

    String hashRefreshToken(String rawToken);

    String issueAccessToken(UUID userId);

    String generateOpaqueToken();

    String hashOpaqueToken(String rawToken);
}
