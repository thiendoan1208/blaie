package com.blaie.blaie_be.capture.api.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMarkDeadRequest(
        @NotBlank(message = "reason must not be blank")
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {
}
