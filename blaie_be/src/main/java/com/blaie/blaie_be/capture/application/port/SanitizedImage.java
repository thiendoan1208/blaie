package com.blaie.blaie_be.capture.application.port;

public record SanitizedImage(
        byte[] bytes,
        String contentType,
        String extension,
        int width,
        int height,
        String sha256
) {
    public SanitizedImage {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
