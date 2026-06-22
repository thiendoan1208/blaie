package com.blaie.blaie_be.auth.api.request;

import com.blaie.blaie_be.auth.domain.AuthValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterLocalRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 32)
        @Pattern(regexp = AuthValidation.USERNAME_PATTERN,
                message = "Username may contain only letters, numbers, dots, underscores, or hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Display name is required")
        @Size(max = AuthValidation.DISPLAY_NAME_MAX_LENGTH)
        @Pattern(regexp = AuthValidation.DISPLAY_NAME_PATTERN,
                message = "Display name contains unsupported characters")
        String displayName,

        @NotBlank(message = "Password is required")
        @Size(min = AuthValidation.PASSWORD_MIN_LENGTH, max = AuthValidation.PASSWORD_MAX_LENGTH,
                message = "Password must contain 8-16 characters")
        @Pattern(regexp = AuthValidation.PASSWORD_UPPERCASE_PATTERN,
                message = "Password must include at least one uppercase letter")
        @Pattern(regexp = AuthValidation.PASSWORD_SPECIAL_CHARACTER_PATTERN,
                message = "Password must include at least one special character")
        String password
) {
    public RegisterLocalRequest {
        username = AuthValidation.trim(username);
        email = AuthValidation.trim(email);
        displayName = AuthValidation.trim(displayName);
        password = AuthValidation.trim(password);
    }
}
