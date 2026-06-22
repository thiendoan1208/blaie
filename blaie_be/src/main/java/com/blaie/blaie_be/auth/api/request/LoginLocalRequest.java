package com.blaie.blaie_be.auth.api.request;

import com.blaie.blaie_be.auth.domain.AuthValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginLocalRequest(
        @NotBlank(message = "Username or email is required")
        @Size(max = 255)
        String identifier,

        @NotBlank(message = "Password is required")
        @Size(min = AuthValidation.PASSWORD_MIN_LENGTH, max = AuthValidation.PASSWORD_MAX_LENGTH,
                message = "Password must contain 8-16 characters")
        @Pattern(regexp = AuthValidation.PASSWORD_UPPERCASE_PATTERN,
                message = "Password must include at least one uppercase letter")
        @Pattern(regexp = AuthValidation.PASSWORD_SPECIAL_CHARACTER_PATTERN,
                message = "Password must include at least one special character")
        String password
) {
    public LoginLocalRequest {
        identifier = AuthValidation.trim(identifier);
        password = AuthValidation.trim(password);
    }
}
