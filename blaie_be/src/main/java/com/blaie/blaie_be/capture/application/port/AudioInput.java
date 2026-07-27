package com.blaie.blaie_be.capture.application.port;

import java.util.Arrays;

public record AudioInput(
        String filename,
        String contentType,
        byte[] bytes
) {
    public AudioInput {
        bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
