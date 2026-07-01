package com.blaie.blaie_be.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        String currentPassword,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 16, message = "Password must contain 8-16 characters")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must include at least one uppercase letter")
        @Pattern(regexp = ".*[^A-Za-z0-9\\s].*", message = "Password must include at least one special character")
        String newPassword
) {
}
