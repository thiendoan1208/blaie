package com.blaie.blaie_be.auth.application.port;

import java.net.URI;

public interface GoogleOAuthSettingsPort {
    String clientId();

    URI redirectUri();

    String webBaseUrl();

    URI authorizationUri();
}
