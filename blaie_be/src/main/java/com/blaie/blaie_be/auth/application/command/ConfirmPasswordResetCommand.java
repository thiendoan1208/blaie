package com.blaie.blaie_be.auth.application.command;

public record ConfirmPasswordResetCommand(
        String email,
        String code,
        String newPassword
) {
}
