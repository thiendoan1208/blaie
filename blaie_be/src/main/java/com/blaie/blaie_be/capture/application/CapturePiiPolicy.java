package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CapturePrivacySettingsPort;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CapturePiiMode;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CapturePiiPolicy {
    private static final Pattern ANY_PII_PLACEHOLDER = Pattern.compile(
            "__BLAIE_PII_[A-Z]+_[a-f0-9]{32}_\\d+__"
    );
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\w.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\w.])"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}(?![A-Z0-9._%+-])"
    );
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile(
            "(?<!\\w)\\+\\d(?:[\\s().-]*\\d){6,14}(?!\\w)"
    );
    private static final Pattern NORTH_AMERICAN_PHONE = Pattern.compile(
            "(?<!\\d)(?:\\(\\d{3}\\)|\\d{3})[ .-]\\d{3}[ .-]\\d{4}(?!\\d)"
    );

    private final CapturePrivacySettingsPort settings;

    public CapturePiiPolicy(CapturePrivacySettingsPort settings) {
        this.settings = settings;
    }

    public PreparedText prepare(String originalText) {
        if (settings.piiMode() == CapturePiiMode.ALLOW) {
            return new PreparedText(originalText, Map.of());
        }

        String nonce = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> replacements = new LinkedHashMap<>();
        String masked = mask(originalText, IPV4, "IP", nonce, replacements);
        masked = mask(masked, EMAIL, "EMAIL", nonce, replacements);
        masked = mask(masked, INTERNATIONAL_PHONE, "PHONE", nonce, replacements);
        masked = mask(masked, NORTH_AMERICAN_PHONE, "PHONE", nonce, replacements);
        return new PreparedText(masked, replacements);
    }

    public CaptureAnalysis restore(PreparedText prepared, CaptureAnalysis analysis) {
        if (prepared.replacements().isEmpty()) {
            return analysis;
        }
        requireExactPlaceholders(prepared, analysis);
        List<ClassifiedTextItem> restoredItems = analysis.items().stream()
                .map(item -> new ClassifiedTextItem(
                        restoreText(item.originalText(), prepared.replacements()),
                        item.category()
                ))
                .toList();
        return new CaptureAnalysis(
                restoredItems,
                analysis.provider(),
                analysis.model(),
                analysis.promptVersion()
        );
    }

    private void requireExactPlaceholders(PreparedText prepared, CaptureAnalysis analysis) {
        StringBuilder output = new StringBuilder();
        for (ClassifiedTextItem item : analysis.items()) {
            output.append(item.originalText()).append('\n');
        }
        String unrecognized = output.toString();
        boolean invalid = false;
        for (String placeholder : prepared.replacements().keySet()) {
            int first = unrecognized.indexOf(placeholder);
            int last = unrecognized.lastIndexOf(placeholder);
            if (first >= 0 && first != last) {
                invalid = true;
                break;
            }
            unrecognized = unrecognized.replace(placeholder, "");
        }
        if (invalid || ANY_PII_PLACEHOLDER.matcher(unrecognized).find() || unrecognized.contains("__BLAIE_PII_")) {
            throw new com.blaie.blaie_be.capture.domain.TextClassificationException(
                    "ai_invalid_response",
                    "AI response did not preserve privacy placeholders",
                    com.blaie.blaie_be.capture.domain.TextClassificationFailureClass.PROVIDER_RETRYABLE
            );
        }
    }

    private String mask(
            String input,
            Pattern pattern,
            String type,
            String nonce,
            Map<String, String> replacements
    ) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String placeholder = "__BLAIE_PII_" + type + "_" + nonce + "_" + (replacements.size() + 1) + "__";
            replacements.put(placeholder, matcher.group());
            matcher.appendReplacement(output, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String restoreText(String text, Map<String, String> replacements) {
        String restored = text;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            restored = restored.replace(replacement.getKey(), replacement.getValue());
        }
        return restored;
    }

    public record PreparedText(String providerText, Map<String, String> replacements) {
        public PreparedText {
            replacements = Map.copyOf(replacements);
        }
    }
}
