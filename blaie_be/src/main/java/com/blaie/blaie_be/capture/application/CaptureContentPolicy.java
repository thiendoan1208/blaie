package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CaptureContentPolicy {
    private static final Pattern PAYMENT_CARD_CANDIDATE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern US_SSN = Pattern.compile("(?<!\\d)\\d{3}-\\d{2}-\\d{4}(?!\\d)");
    private static final Pattern RESERVED_PRIVACY_TOKEN = Pattern.compile("__BLAIE_PII_");
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bASIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bglpat-[A-Za-z0-9_-]{20,}\\b"),
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
            Pattern.compile("\\bsk_live_[A-Za-z0-9]{16,}\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{30,}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
            Pattern.compile("(?i)\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis)://[^\\s:/]+:[^\\s@/]+@")
    );

    public void validate(String text) {
        if (RESERVED_PRIVACY_TOKEN.matcher(text).find()) {
            throw new TextClassificationException(
                    "reserved_privacy_token_detected",
                    "Capture contains a reserved privacy token",
                    TextClassificationFailureClass.CONTENT_TERMINAL
            );
        }
        boolean containsSecret = SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
        if (containsSecret) {
            throw new TextClassificationException(
                    "sensitive_credential_detected",
                    "Capture contains a credential-like secret",
                    TextClassificationFailureClass.CONTENT_TERMINAL
            );
        }
        if (US_SSN.matcher(text).find() || containsValidPaymentCard(text)) {
            throw new TextClassificationException(
                    "sensitive_personal_identifier_detected",
                    "Capture contains a high-risk personal identifier",
                    TextClassificationFailureClass.CONTENT_TERMINAL
            );
        }
    }

    private boolean containsValidPaymentCard(String text) {
        var matcher = PAYMENT_CARD_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) return true;
        }
        return false;
    }

    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
