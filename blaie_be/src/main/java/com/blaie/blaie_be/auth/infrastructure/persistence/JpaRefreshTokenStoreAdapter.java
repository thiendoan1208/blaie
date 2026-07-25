package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.RefreshTokenStorePort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenView;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaRefreshTokenStoreAdapter implements RefreshTokenStorePort {
    private final UserRepository userRepository;
    private final RefreshTokenRepository tokenRepository;

    public JpaRefreshTokenStoreAdapter(
            UserRepository userRepository,
            RefreshTokenRepository tokenRepository
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void createWebRefreshToken(
            UUID userId,
            String tokenHash,
            UUID tokenFamilyId,
            Instant expiresAt,
            String userAgent
    ) {
        tokenRepository.save(RefreshTokenEntity.webCookie(
                requireUser(userId),
                tokenHash,
                tokenFamilyId,
                expiresAt,
                userAgent
        ));
    }

    @Override
    public Optional<RefreshTokenView> findByTokenHash(String tokenHash) {
        return tokenRepository.findByTokenHash(tokenHash)
                .map(AuthPersistenceMapper::toRefreshTokenView);
    }

    @Override
    public void rotateWebRefreshToken(
            String currentTokenHash,
            String nextTokenHash,
            Instant expiresAt,
            String userAgent,
            Instant now
    ) {
        RefreshTokenEntity current = tokenRepository.findByTokenHash(currentTokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        RefreshTokenEntity next = tokenRepository.save(RefreshTokenEntity.webCookie(
                current.user(),
                nextTokenHash,
                current.tokenFamilyId(),
                expiresAt,
                userAgent
        ));
        current.markUsed(now);
        current.revoke("rotated", next, now);
    }

    @Override
    public void revokeRefreshToken(String tokenHash, String reason, Instant now) {
        tokenRepository.findByTokenHash(tokenHash)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.markUsed(now);
                    token.revoke(reason, null, now);
                });
    }

    @Override
    public void revokeFamily(UUID tokenFamilyId, String reason, Instant now) {
        tokenRepository.revokeFamily(tokenFamilyId, reason, now);
    }

    @Override
    public void revokeAllUserTokens(UUID userId, String reason, Instant now) {
        tokenRepository.revokeAllUserTokens(userId, reason, now);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }
}
