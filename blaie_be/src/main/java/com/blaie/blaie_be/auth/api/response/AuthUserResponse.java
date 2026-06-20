package com.blaie.blaie_be.auth.api.response;

import com.blaie.blaie_be.auth.application.result.AuthUserResult;
import java.time.Instant;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        Instant createdAt
) {
    public static AuthUserResponse from(AuthUserResult result) {
        return new AuthUserResponse(
                result.id(),
                result.username(),
                result.email(),
                result.displayName(),
                result.avatarUrl(),
                result.createdAt()
        );
    }
}
