package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.CapturePiiMode;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapturePiiPolicyTest {
    @Test
    void masksStructuredPiiBeforeProviderAndRestoresExactUnicodeText() {
        CapturePiiPolicy policy = policy(CapturePiiMode.MASK_STRUCTURED);
        String original = "Nhắc José email jane@example.com, gọi +1 (416) 555-0199 từ 192.168.1.10";

        CapturePiiPolicy.PreparedText prepared = policy.prepare(original);

        assertThat(prepared.providerText())
                .doesNotContain("jane@example.com", "+1 (416) 555-0199", "192.168.1.10")
                .contains("__BLAIE_PII_EMAIL_", "__BLAIE_PII_PHONE_", "__BLAIE_PII_IP_");
        CaptureAnalysis restored = policy.restore(prepared, analysis(prepared.providerText()));
        assertThat(restored.items().getFirst().originalText()).isEqualTo(original);
    }

    @Test
    void doesNotMaskDatesTimesOrOrdinaryNumbers() {
        CapturePiiPolicy.PreparedText prepared = policy(CapturePiiMode.MASK_STRUCTURED)
                .prepare("Meet 2026-07-18 at 17:30, room 204");

        assertThat(prepared.providerText()).isEqualTo("Meet 2026-07-18 at 17:30, room 204");
        assertThat(prepared.replacements()).isEmpty();
    }

    @Test
    void allowModePassesRawTextThrough() {
        String original = "Email jane@example.com";
        CapturePiiPolicy.PreparedText prepared = policy(CapturePiiMode.ALLOW).prepare(original);

        assertThat(prepared.providerText()).isEqualTo(original);
        assertThat(prepared.replacements()).isEmpty();
    }

    @Test
    void allowsOmittedInactivePiiButRejectsDuplicatedAlteredAndUnknownPrivacyPlaceholders() {
        CapturePiiPolicy policy = policy(CapturePiiMode.MASK_STRUCTURED);
        CapturePiiPolicy.PreparedText prepared = policy.prepare("Email jane@example.com");
        String placeholder = prepared.replacements().keySet().iterator().next();

        CaptureAnalysis omitted = policy.restore(prepared, analysis("Email was removed"));
        assertThat(omitted.items().getFirst().originalText()).isEqualTo("Email was removed");
        CaptureAnalysis empty = policy.restore(prepared, new CaptureAnalysis(List.of(), "test", "test-model", "v5"));
        assertThat(empty.items()).isEmpty();

        for (String invalid : List.of(
                placeholder + " and " + placeholder,
                placeholder + "__BLAIE_PII_EMAIL_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_9__",
                placeholder + " __BLAIE_PII_broken__"
        )) {
            assertThatThrownBy(() -> policy.restore(prepared, analysis(invalid)))
                    .isInstanceOfSatisfying(TextClassificationException.class, exception ->
                            assertThat(exception.failureCode()).isEqualTo("ai_invalid_response"));
        }
    }

    private CapturePiiPolicy policy(CapturePiiMode mode) {
        return new CapturePiiPolicy(() -> mode);
    }

    private CaptureAnalysis analysis(String text) {
        return new CaptureAnalysis(
                List.of(new ClassifiedTextItem(text, CaptureCategory.INFORMATION)),
                "test",
                "test-model",
                "v5"
        );
    }
}
