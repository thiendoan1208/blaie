package com.blaie.blaie_be.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginLocalRequest(
        @NotBlank
        @Size(max = 255)
        String identifier,

        @NotBlank
        @Size(min = 8, max = 128)
        String password
) {
}
