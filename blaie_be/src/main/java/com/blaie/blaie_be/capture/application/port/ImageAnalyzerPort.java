package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.domain.CaptureAnalysis;

public interface ImageAnalyzerPort {
    CaptureAnalysis analyze(ImageAnalysisInput input);
}
