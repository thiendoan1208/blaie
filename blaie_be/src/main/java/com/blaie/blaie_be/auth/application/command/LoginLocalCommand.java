package com.blaie.blaie_be.auth.application.command;

public record LoginLocalCommand(
        String identifier,
        String password
) {
}
