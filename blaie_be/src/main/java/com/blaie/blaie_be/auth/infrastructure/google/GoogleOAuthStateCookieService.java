package com.blaie.blaie_be.auth.infrastructure.google;

import com.blaie.blaie_be.auth.infrastructure.security.AuthProperties;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthStateCookieService {
    public static final String COOKIE_NAME = "blaie_google_oauth";

    private static final String COOKIE_PATH = "/api/v1/auth/google";
    private static final String DEFAULT_NEXT_PATH = "/inbox";
    private static final int RANDOM_BYTES = 32;

    private final GoogleOAuthProperties googleOAuthProperties;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

    public GoogleOAuthStateCookieService(
            GoogleOAuthProperties googleOAuthProperties,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public GoogleOAuthState create(String requestedNextPath) {
        String state = randomToken();
        String codeVerifier = randomToken();
        String codeChallenge = codeChallenge(codeVerifier);
        String nextPath = sanitizeNextPath(requestedNextPath);
        return new GoogleOAuthState(state, codeVerifier, codeChallenge, nextPath);
    }

    public ResponseCookie cookie(GoogleOAuthState state) {
        Instant expiresAt = clock.instant().plus(googleOAuthProperties.stateTtl());
        String payload = String.join("\n",
                state.state(),
                state.codeVerifier(),
                state.nextPath(),
                String.valueOf(expiresAt.getEpochSecond())
        );
        String encodedPayload = base64UrlEncoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return ResponseCookie.from(COOKIE_NAME, encodedPayload + "." + sign(encodedPayload))
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(googleOAuthProperties.stateTtl())
                .build();
    }

    public GoogleOAuthState require(String cookieValue, String stateParameter) {
        if (cookieValue == null || cookieValue.isBlank() || stateParameter == null || stateParameter.isBlank()) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String[] parts = cookieValue.split("\\.", 2);
        if (parts.length != 2 || !signatureMatches(parts[0], parts[1])) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String payload = new String(base64UrlDecoder.decode(parts[0]), StandardCharsets.UTF_8);
        String[] payloadParts = payload.split("\n", -1);
        if (payloadParts.length != 4) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String state = payloadParts[0];
        String codeVerifier = payloadParts[1];
        String nextPath = payloadParts[2];
        Instant expiresAt = Instant.ofEpochSecond(parseEpochSecond(payloadParts[3]));
        if (!state.equals(stateParameter) || !expiresAt.isAfter(clock.instant())) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        return new GoogleOAuthState(state, codeVerifier, codeChallenge(codeVerifier), sanitizeNextPath(nextPath));
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private String randomToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return base64UrlEncoder.encodeToString(bytes);
    }

    private String codeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64UrlEncoder.encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String sanitizeNextPath(String requestedNextPath) {
        if (requestedNextPath == null || requestedNextPath.isBlank()) {
            return DEFAULT_NEXT_PATH;
        }
        String value = requestedNextPath.trim();
        if (value.length() > 512
                || !value.startsWith("/")
                || value.startsWith("//")
                || value.contains("\\")
                || value.contains("://")
                || value.chars().anyMatch(Character::isISOControl)) {
            return DEFAULT_NEXT_PATH;
        }
        return value;
    }

    private long parseEpochSecond(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private boolean signatureMatches(String encodedPayload, String signature) {
        return MessageDigest.isEqual(
                sign(encodedPayload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authProperties.accessTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64UrlEncoder.encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
