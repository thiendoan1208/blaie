package com.blaie.blaie_be.auth.infrastructure.security;

import com.blaie.blaie_be.auth.application.port.WebAuthCookiePort;
import com.blaie.blaie_be.core.security.AuthCookieNames;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService implements WebAuthCookiePort {
    public static final String ACCESS_COOKIE_NAME = AuthCookieNames.ACCESS_COOKIE_NAME;
    public static final String REFRESH_COOKIE_NAME = AuthCookieNames.REFRESH_COOKIE_NAME;

    private static final String ACCESS_COOKIE_PATH = "/";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties authProperties;

    public AuthCookieService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public String accessCookie(String accessToken, Duration maxAge) {
        return cookie(ACCESS_COOKIE_NAME, accessToken, ACCESS_COOKIE_PATH, maxAge);
    }

    @Override
    public String refreshCookie(String refreshToken, Duration maxAge) {
        return cookie(REFRESH_COOKIE_NAME, refreshToken, REFRESH_COOKIE_PATH, maxAge);
    }

    @Override
    public String clearAccessCookie() {
        return cookie(ACCESS_COOKIE_NAME, "", ACCESS_COOKIE_PATH, Duration.ZERO);
    }

    @Override
    public String clearRefreshCookie() {
        return cookie(REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH, Duration.ZERO);
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
