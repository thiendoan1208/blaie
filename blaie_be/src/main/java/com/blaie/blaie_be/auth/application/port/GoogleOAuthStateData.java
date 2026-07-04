package com.blaie.blaie_be.auth.application.port;

public record GoogleOAuthStateData(
        String state,
        String codeVerifier,
        String codeChallenge,
        String nextPath
) {
}
