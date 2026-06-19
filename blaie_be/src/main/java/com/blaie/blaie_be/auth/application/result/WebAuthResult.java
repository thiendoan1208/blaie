package com.blaie.blaie_be.auth.application.result;

import com.blaie.blaie_be.auth.api.response.AuthUserResponse;
import java.time.Duration;

public record WebAuthResult(
        AuthUserResponse user,
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl
) {
}
