package com.blaie.blaie_be.auth.api.response;

public record CsrfTokenResponse(
        String token,
        String headerName
) {
}
