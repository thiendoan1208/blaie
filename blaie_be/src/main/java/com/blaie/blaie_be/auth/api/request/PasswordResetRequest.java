package com.blaie.blaie_be.auth.api.request;

import com.blaie.blaie_be.auth.domain.AuthValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255)
        String email
) {
    public PasswordResetRequest {
        email = AuthValidation.trim(email);
    }
}
