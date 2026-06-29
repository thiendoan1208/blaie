package com.blaie.blaie_be.auth.application.command;

public record UpdatePasswordCommand(
        String currentPassword,
        String newPassword
) {
}
