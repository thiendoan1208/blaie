package com.blaie.blaie_be.auth.application.port;

public interface PasswordHasherPort {
    String encode(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
