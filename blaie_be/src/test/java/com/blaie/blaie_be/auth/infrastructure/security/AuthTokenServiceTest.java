package com.blaie.blaie_be.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuthTokenServiceTest {
    private static final String ACTIVE_SECRET = "unit-test-active-secret-that-is-at-least-32-bytes";
    private static final String PREVIOUS_SECRET = "unit-test-previous-secret-that-is-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-06-18T12:00:00Z");

    @Test
    void issuedTokenCanBeParsed() {
        UUID userId = UUID.randomUUID();
        AuthTokenService tokenService = tokenServiceAt(NOW);

        String token = tokenService.issueAccessToken(userId);

        Assertions.assertThat(tokenService.parseAccessToken(token))
                .hasValueSatisfying(claims -> {
                    Assertions.assertThat(claims.userId()).isEqualTo(userId);
                    Assertions.assertThat(claims.tokenId()).isNotNull();
                    Assertions.assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
                });
    }

    @Test
    void tokenSignedByPreviousKeyRemainsValidDuringRotation() {
        UUID userId = UUID.randomUUID();
        AuthTokenService previousTokenService = tokenServiceAt(NOW, "v1", PREVIOUS_SECRET, null, null);
        String previousToken = previousTokenService.issueAccessToken(userId);

        AuthTokenService rotatedTokenService = tokenServiceAt(
                NOW,
                "v2",
                ACTIVE_SECRET,
                "v1",
                PREVIOUS_SECRET
        );

        Assertions.assertThat(rotatedTokenService.parseAccessToken(previousToken))
                .hasValueSatisfying(claims -> Assertions.assertThat(claims.userId()).isEqualTo(userId));
    }

    @Test
    void tokenSignedByUnknownKeyIsRejected() {
        String token = tokenServiceAt(NOW, "unknown", PREVIOUS_SECRET, null, null)
                .issueAccessToken(UUID.randomUUID());

        Assertions.assertThat(tokenServiceAt(NOW).parseAccessToken(token)).isEmpty();
    }

    @Test
    void tokenWithDifferentIssuerOrAudienceIsRejected() {
        AuthProperties foreignProperties = properties("v1", ACTIVE_SECRET, null, null);
        foreignProperties.setAccessTokenIssuer("another-issuer");
        foreignProperties.setAccessTokenAudience("another-audience");
        String token = new AuthTokenService(foreignProperties, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper())
                .issueAccessToken(UUID.randomUUID());

        Assertions.assertThat(tokenServiceAt(NOW).parseAccessToken(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = tokenServiceAt(NOW).issueAccessToken(userId);

        Assertions.assertThat(tokenServiceAt(NOW.plus(Duration.ofMinutes(16))).parseAccessToken(token)).isEmpty();
    }

    @Test
    void forgedSignatureIsRejected() {
        String token = tokenServiceAt(NOW).issueAccessToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        byte[] forgedSignature = Base64.getUrlDecoder().decode(parts[2]);
        forgedSignature[0] ^= 1;
        String forgedToken = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedSignature);

        Assertions.assertThat(tokenServiceAt(NOW).parseAccessToken(forgedToken)).isEmpty();
    }

    @Test
    void malformedTokensAreRejected() {
        AuthTokenService tokenService = tokenServiceAt(NOW);

        Assertions.assertThat(tokenService.parseAccessToken(null)).isEmpty();
        Assertions.assertThat(tokenService.parseAccessToken("")).isEmpty();
        Assertions.assertThat(tokenService.parseAccessToken("not-a-jwt")).isEmpty();
        Assertions.assertThat(tokenService.parseAccessToken("a.b.c.d")).isEmpty();
    }

    private AuthTokenService tokenServiceAt(Instant instant) {
        return tokenServiceAt(instant, "v1", ACTIVE_SECRET, null, null);
    }

    private AuthTokenService tokenServiceAt(
            Instant instant,
            String activeKeyId,
            String activeSecret,
            String previousKeyId,
            String previousSecret
    ) {
        return new AuthTokenService(
                properties(activeKeyId, activeSecret, previousKeyId, previousSecret),
                Clock.fixed(instant, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    private AuthProperties properties(
            String activeKeyId,
            String activeSecret,
            String previousKeyId,
            String previousSecret
    ) {
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenSecret(activeSecret);
        properties.setAccessTokenActiveKeyId(activeKeyId);
        properties.setAccessTokenPreviousKeyId(previousKeyId);
        properties.setAccessTokenPreviousSecret(previousSecret);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        return properties;
    }
}
