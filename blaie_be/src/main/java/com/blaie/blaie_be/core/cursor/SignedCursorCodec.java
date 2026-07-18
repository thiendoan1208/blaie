package com.blaie.blaie_be.core.cursor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SignedCursorCodec {
    private static final String VERSION = "v1";
    private static final int MAX_CURSOR_LENGTH = 2_048;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final CursorProperties properties;

    public SignedCursorCodec(CursorProperties properties) {
        this.properties = properties;
    }

    public String encode(String audience, String payload) {
        requireAudience(audience);
        if (payload == null || payload.isBlank()) {
            throw invalidCursor();
        }
        String payloadPart = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = signingInput(properties.activeKeyId(), audience, payloadPart);
        String signature = ENCODER.encodeToString(hmac(properties.activeSecret(), signingInput));
        return VERSION + "." + properties.activeKeyId() + "." + payloadPart + "." + signature;
    }

    public String decode(String audience, String cursor) {
        requireAudience(audience);
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw invalidCursor();
            }
            String secret = secretFor(parts[1]);
            byte[] presentedSignature = DECODER.decode(parts[3]);
            byte[] expectedSignature = hmac(secret, signingInput(parts[1], audience, parts[2]));
            if (!MessageDigest.isEqual(expectedSignature, presentedSignature)) {
                throw invalidCursor();
            }
            String payload = new String(DECODER.decode(parts[2]), StandardCharsets.UTF_8);
            if (payload.isBlank()) {
                throw invalidCursor();
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private String secretFor(String keyId) {
        if (properties.activeKeyId().equals(keyId)) {
            return properties.activeSecret();
        }
        if (properties.previousKeyId() != null && properties.previousKeyId().equals(keyId)) {
            return properties.previousSecret();
        }
        throw invalidCursor();
    }

    private String signingInput(String keyId, String audience, String payloadPart) {
        return VERSION + "\n" + keyId + "\n" + audience + "\n" + payloadPart;
    }

    private byte[] hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private void requireAudience(String audience) {
        if (audience == null || !audience.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("cursor audience is invalid");
        }
    }

    private IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("cursor is invalid");
    }
}
