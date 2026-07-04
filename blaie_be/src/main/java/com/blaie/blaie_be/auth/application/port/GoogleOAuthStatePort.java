package com.blaie.blaie_be.auth.application.port;

public interface GoogleOAuthStatePort {
    String COOKIE_NAME = "blaie_google_oauth";

    GoogleOAuthStateData create(String next);

    GoogleOAuthStateData require(String rawCookie, String returnedState);

    String cookie(GoogleOAuthStateData state);

    String clearCookie();
}
