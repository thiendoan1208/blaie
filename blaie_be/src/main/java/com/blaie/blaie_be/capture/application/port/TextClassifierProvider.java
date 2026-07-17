package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.domain.CaptureAnalysis;

public interface TextClassifierProvider {
    String providerId();

    CaptureAnalysis classify(String text);
}
