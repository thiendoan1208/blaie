package com.blaie.blaie_be.capture.application.port;

public interface ImageSanitizerPort {
    SanitizedImage sanitize(ImageInput input);
}
