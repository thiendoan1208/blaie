package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.AudioInput;

public interface AudioTranscriptionService {
    String transcribe(AudioInput audio, String language);
}
