package com.blaie.blaie_be.core.cursor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedCursorCodecTest {
    @Test
    void roundTripsWithTheActiveKeyAndRejectsWrongAudience() {
        SignedCursorCodec codec = codec("current", "current-cursor-secret-at-least-32-characters");

        String cursor = codec.encode("inbox-items", "payload|123");

        assertThat(cursor).startsWith("v1.current.");
        assertThat(codec.decode("inbox-items", cursor)).isEqualTo("payload|123");
        assertThatThrownBy(() -> codec.decode("admin-processing-jobs", cursor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cursor is invalid");
    }

    @Test
    void acceptsThePreviousKeyDuringRotation() {
        SignedCursorCodec oldCodec = codec("old", "old-cursor-secret-at-least-32-characters-1");
        CursorProperties rotated = properties("new", "new-cursor-secret-at-least-32-characters-1");
        rotated.setPreviousKeyId("old");
        rotated.setPreviousSecret("old-cursor-secret-at-least-32-characters-1");
        SignedCursorCodec newCodec = new SignedCursorCodec(rotated);

        assertThat(newCodec.decode("inbox-items", oldCodec.encode("inbox-items", "payload")))
                .isEqualTo("payload");
    }

    @Test
    void rejectsTamperingMalformedUnknownAndOversizedCursors() {
        SignedCursorCodec codec = codec("current", "current-cursor-secret-at-least-32-characters");
        String valid = codec.encode("inbox-items", "payload");
        String tamperedPayload = valid.replace("cGF5bG9hZA", "dGFtcGVyZWQ");
        String tamperedSignature = valid.substring(0, valid.length() - 1)
                + (valid.endsWith("A") ? "B" : "A");

        for (String invalid : new String[] {
                tamperedPayload,
                tamperedSignature,
                valid.replace(".current.", ".unknown."),
                "not-a-cursor",
                "x".repeat(2_049)
        }) {
            assertThatThrownBy(() -> codec.decode("inbox-items", invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("cursor is invalid");
        }
    }

    private SignedCursorCodec codec(String keyId, String secret) {
        return new SignedCursorCodec(properties(keyId, secret));
    }

    private CursorProperties properties(String keyId, String secret) {
        CursorProperties properties = new CursorProperties();
        properties.setActiveKeyId(keyId);
        properties.setActiveSecret(secret);
        return properties;
    }
}
