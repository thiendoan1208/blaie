package com.blaie.blaie_be.auth.api.request;

import com.blaie.blaie_be.auth.domain.AuthValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginLocalRequest(
        @NotBlank(message = "Username or email is required")
        @Size(max = 255)
        String identifier,

        @NotBlank(message = "Password is required")
        String password
) {
    public LoginLocalRequest {
        identifier = AuthValidation.trim(identifier);
        password = AuthValidation.trim(password);
    }
}
