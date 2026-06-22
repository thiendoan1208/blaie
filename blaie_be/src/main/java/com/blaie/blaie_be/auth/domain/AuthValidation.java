package com.blaie.blaie_be.auth.domain;

public final class AuthValidation {
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 16;
    public static final int DISPLAY_NAME_MAX_LENGTH = 32;

    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]+$";
    public static final String DISPLAY_NAME_PATTERN = "^[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .'-]*$";
    public static final String PASSWORD_UPPERCASE_PATTERN = ".*[A-Z].*";
    public static final String PASSWORD_SPECIAL_CHARACTER_PATTERN = ".*[^A-Za-z0-9\\s].*";

    private AuthValidation() {
    }

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
