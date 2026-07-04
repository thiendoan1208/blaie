package com.blaie.blaie_be.auth.application.port;

import java.time.Duration;

public interface WebAuthCookiePort {
    String accessCookie(String accessToken, Duration maxAge);

    String refreshCookie(String refreshToken, Duration maxAge);

    String clearAccessCookie();

    String clearRefreshCookie();
}
