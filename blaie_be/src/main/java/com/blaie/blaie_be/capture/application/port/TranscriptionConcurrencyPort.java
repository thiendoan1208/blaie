package com.blaie.blaie_be.capture.application.port;

public interface TranscriptionConcurrencyPort {
    Permit acquire();

    @FunctionalInterface
    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
