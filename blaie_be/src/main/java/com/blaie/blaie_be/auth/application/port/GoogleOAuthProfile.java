package com.blaie.blaie_be.auth.application.port;

public record GoogleOAuthProfile(
        String subject,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
