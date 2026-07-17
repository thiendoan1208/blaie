package com.blaie.blaie_be.capture.infrastructure.ai;

public interface ProviderConcurrencyLimiter {
    Permit acquire(String providerId);

    @FunctionalInterface
    interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
