package com.blaie.blaie_be.auth.application;

import java.util.UUID;

public interface EmailVerificationService {
    void sendInitialVerification(UUID userId);

    void resendVerificationForCurrentUser();

    void verifyEmailToken(String rawToken);

    String verifiedRedirectUrl();

    String failedRedirectUrl();
}
