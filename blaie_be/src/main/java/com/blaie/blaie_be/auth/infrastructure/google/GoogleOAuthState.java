package com.blaie.blaie_be.auth.infrastructure.google;

public record GoogleOAuthState(
        String state,
        String codeVerifier,
        String codeChallenge,
        String nextPath
) {
}
