package com.blaie.blaie_be.auth.api.request;

import com.blaie.blaie_be.auth.domain.AuthValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Reset code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Reset code must contain 6 digits")
        String code,

        @NotBlank(message = "Password is required")
        @Size(min = AuthValidation.PASSWORD_MIN_LENGTH, max = AuthValidation.PASSWORD_MAX_LENGTH,
                message = "Password must contain 8-16 characters")
        @Pattern(regexp = AuthValidation.PASSWORD_UPPERCASE_PATTERN,
                message = "Password must include at least one uppercase letter")
        @Pattern(regexp = AuthValidation.PASSWORD_SPECIAL_CHARACTER_PATTERN,
                message = "Password must include at least one special character")
        String newPassword
) {
    public PasswordResetConfirmRequest {
        email = AuthValidation.trim(email);
        code = AuthValidation.trim(code);
        newPassword = AuthValidation.trim(newPassword);
    }
}
