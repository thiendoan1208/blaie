package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.ConfirmPasswordResetCommand;
import com.blaie.blaie_be.auth.application.command.RequestPasswordResetCommand;

public interface PasswordResetService {
    void requestPasswordReset(RequestPasswordResetCommand command);

    void confirmPasswordReset(ConfirmPasswordResetCommand command);
}
