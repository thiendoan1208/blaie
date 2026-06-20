package com.blaie.blaie_be.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthTokenService {
    private static final int MAX_ACCESS_TOKEN_LENGTH = 4096;
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30;
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthProperties authProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuthTokenService(AuthProperties authProperties, Clock clock, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public String issueAccessToken(UUID userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(authProperties.accessTokenTtl());
        String header = base64Url(writeJson(Map.of(
                "alg", "HS256",
                "typ", "JWT",
                "kid", authProperties.accessTokenActiveKeyId()
        )));
        String payload = base64Url(writeJson(Map.of(
                "typ", "access",
                "sub", userId.toString(),
                "iss", authProperties.accessTokenIssuer(),
                "aud", authProperties.accessTokenAudience(),
                "jti", UUID.randomUUID().toString(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        )));
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + base64Url(hmacSha256(unsignedToken, authProperties.accessTokenSecret()));
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_ACCESS_TOKEN_LENGTH) {
            return Optional.empty();
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return Optional.empty();
            }
            JsonNode header = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[0]));
            if (!isTextClaim(header, "alg", "HS256")
                    || !isTextClaim(header, "typ", "JWT")
                    || !header.path("kid").isString()) {
                return Optional.empty();
            }
            Optional<String> signingSecret = authProperties.accessTokenSecretFor(header.path("kid").asString());
            if (signingSecret.isEmpty()) {
                return Optional.empty();
            }
            String unsignedToken = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(unsignedToken, signingSecret.get());
            byte[] actualSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }

            JsonNode payload = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            if (!isTextClaim(payload, "typ", "access")
                    || !isTextClaim(payload, "iss", authProperties.accessTokenIssuer())
                    || !isTextClaim(payload, "aud", authProperties.accessTokenAudience())
                    || !payload.path("sub").isString()
                    || !payload.path("jti").isString()
                    || !payload.path("iat").isIntegralNumber()
                    || !payload.path("exp").isIntegralNumber()) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(payload.path("sub").asString());
            UUID tokenId = UUID.fromString(payload.path("jti").asString());
            Instant issuedAt = Instant.ofEpochSecond(payload.path("iat").asLong());
            Instant expiresAt = Instant.ofEpochSecond(payload.path("exp").asLong());
            Instant now = clock.instant();
            if (!expiresAt.isAfter(now)
                    || !expiresAt.isAfter(issuedAt)
                    || issuedAt.isAfter(now.plusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(userId, tokenId, expiresAt));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public String generateRefreshToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return base64Url(tokenBytes);
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64Url(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    private byte[] hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    private byte[] writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize access token claims", exception);
        }
    }

    private boolean isTextClaim(JsonNode node, String claim, String expectedValue) {
        return node.path(claim).isString() && expectedValue.equals(node.path(claim).asString());
    }

    private String base64Url(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }
}
