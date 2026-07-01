package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.infrastructure.persistence.UserEntity;

public interface EmailVerificationService {
    void sendInitialVerification(UserEntity user);

    void resendVerificationForCurrentUser();

    void verifyEmailToken(String rawToken);

    String verifiedRedirectUrl();

    String failedRedirectUrl();
}
