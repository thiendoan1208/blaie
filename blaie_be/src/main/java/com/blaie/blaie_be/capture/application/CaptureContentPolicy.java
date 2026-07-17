package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaptureContentPolicy {
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}\\b")
    );

    public void validate(String text) {
        boolean containsSecret = SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
        if (containsSecret) {
            throw new TextClassificationException(
                    "sensitive_credential_detected",
                    "Capture contains a credential-like secret",
                    TextClassificationFailureClass.CONTENT_TERMINAL
            );
        }
    }
}
