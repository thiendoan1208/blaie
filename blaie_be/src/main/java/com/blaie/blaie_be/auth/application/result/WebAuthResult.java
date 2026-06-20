package com.blaie.blaie_be.auth.application.result;

import java.time.Duration;

public record WebAuthResult(
        AuthUserResult user,
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl
) {
}
