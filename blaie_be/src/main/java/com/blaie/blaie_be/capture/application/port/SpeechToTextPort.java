package com.blaie.blaie_be.capture.application.port;

public interface SpeechToTextPort {
    String transcribe(AudioInput audio, String language);
}
