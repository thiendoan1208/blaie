package com.blaie.blaie_be.auth.application.port;

import java.net.URI;

public interface GoogleOAuthClientPort {
    GoogleOAuthProfile exchangeCodeForProfile(String code, String codeVerifier, URI redirectUri);
}
