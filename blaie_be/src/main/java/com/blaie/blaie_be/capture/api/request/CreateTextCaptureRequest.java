package com.blaie.blaie_be.capture.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextCaptureRequest(
        @NotBlank @Size(max = 10_000) String text
) {
}
