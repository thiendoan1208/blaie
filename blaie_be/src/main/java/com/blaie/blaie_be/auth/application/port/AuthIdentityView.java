package com.blaie.blaie_be.auth.application.port;

public record AuthIdentityView(
        AuthUserView user,
        String provider,
        String providerSubject,
        boolean emailVerified,
        String passwordHash
) {
}
