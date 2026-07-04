package com.blaie.blaie_be.auth.application.port;

import java.time.Duration;

public interface AuthTokenSettingsPort {
    Duration accessTokenTtl();

    Duration refreshTokenTtl();
}
