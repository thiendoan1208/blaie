package com.blaie.blaie_be.core.security;

import java.util.Objects;
import java.util.Set;

public record CurrentUser(
        String userId,
        String tenantId,
        boolean admin,
        Set<String> permissions
) {
    public CurrentUser {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        permissions = permissions == null || permissions.isEmpty()
                ? Set.of()
                : Set.copyOf(permissions);
        tenantId = tenantId == null || tenantId.isBlank() ? null : tenantId;
    }

    public boolean hasPermission(String action) {
        return admin || permissions.contains(Objects.requireNonNull(action, "action must not be null"));
    }
}
