package com.blaie.blaie_be.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {
    public static final String ACCESS_COOKIE_NAME = "blaie_at";
    public static final String REFRESH_COOKIE_NAME = "blaie_rt";

    private static final String ACCESS_COOKIE_PATH = "/";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties authProperties;

    public AuthCookieService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie accessCookie(String accessToken, Duration maxAge) {
        return cookie(ACCESS_COOKIE_NAME, accessToken, ACCESS_COOKIE_PATH, maxAge);
    }

    public ResponseCookie refreshCookie(String refreshToken, Duration maxAge) {
        return cookie(REFRESH_COOKIE_NAME, refreshToken, REFRESH_COOKIE_PATH, maxAge);
    }

    public ResponseCookie clearAccessCookie() {
        return cookie(ACCESS_COOKIE_NAME, "", ACCESS_COOKIE_PATH, Duration.ZERO);
    }

    public ResponseCookie clearRefreshCookie() {
        return cookie(REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH, Duration.ZERO);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
