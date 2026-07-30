package com.blaie.blaie_be.capture.application.port;

public record ImageInput(
        String originalFilename,
        String declaredContentType,
        byte[] bytes
) {
    public ImageInput {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
