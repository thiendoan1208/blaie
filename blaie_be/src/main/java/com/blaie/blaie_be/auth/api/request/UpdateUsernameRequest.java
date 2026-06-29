package com.blaie.blaie_be.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUsernameRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 32, message = "Username must contain 3-32 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use only letters, numbers, dots, underscores, or hyphens")
        String username
) {
}
