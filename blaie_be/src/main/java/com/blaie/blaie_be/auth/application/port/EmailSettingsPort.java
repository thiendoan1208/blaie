package com.blaie.blaie_be.auth.application.port;

import java.time.Duration;

public interface EmailSettingsPort {
    String webBaseUrl();

    String apiBaseUrl();

    Duration verificationTtl();

    Duration passwordResetTtl();

    Duration verificationResendCooldown();

    Duration verificationResendQuotaWindow();

    long verificationResendQuotaLimit();
}
