package com.blaie.blaie_be.auth.application.command;

public record RegisterLocalCommand(
        String username,
        String email,
        String displayName,
        String password
) {
}
