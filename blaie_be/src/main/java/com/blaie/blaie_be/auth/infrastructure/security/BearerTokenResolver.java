package com.blaie.blaie_be.auth.infrastructure.security;

import java.util.Optional;

public final class BearerTokenResolver {

    private BearerTokenResolver() {
        // Prevent instantiation
    }

    public static Optional<String> resolve(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String[] parts = authorizationHeader.trim().split("\\s+", 2);
        if (parts.length == 2 && "Bearer".equalsIgnoreCase(parts[0])) {
            String token = parts[1].trim();
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }
}
