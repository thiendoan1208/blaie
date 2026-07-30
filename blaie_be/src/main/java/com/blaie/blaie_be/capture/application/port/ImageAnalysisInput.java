package com.blaie.blaie_be.capture.application.port;

public record ImageAnalysisInput(
        String text,
        String contentType,
        byte[] imageBytes
) {
    public ImageAnalysisInput {
        imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
    }

    @Override
    public byte[] imageBytes() {
        return imageBytes.clone();
    }
}
