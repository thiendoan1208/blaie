package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthActionTokenStorePort;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenView;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthActionTokenStoreAdapter implements AuthActionTokenStorePort {
    private final UserRepository userRepository;
    private final AuthActionTokenRepository tokenRepository;

    public JpaAuthActionTokenStoreAdapter(
            UserRepository userRepository,
            AuthActionTokenRepository tokenRepository
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void createToken(UUID userId, String type, String tokenHash, Instant expiresAt) {
        tokenRepository.save(AuthActionTokenEntity.create(
                requireUser(userId),
                type,
                tokenHash,
                expiresAt
        ));
    }

    @Override
    public Optional<AuthActionTokenView> findByTokenHashAndType(String tokenHash, String type) {
        return tokenRepository.findByTokenHashAndType(tokenHash, type)
                .map(AuthPersistenceMapper::toActionTokenView);
    }

    @Override
    public Optional<AuthActionTokenView> findLatestPendingForUpdate(UUID userId, String type) {
        return tokenRepository.findLatestPendingForUpdate(userId, type)
                .map(AuthPersistenceMapper::toActionTokenView);
    }

    @Override
    public void consumeToken(UUID tokenId, Instant now) {
        tokenRepository.findById(tokenId).ifPresent(token -> token.consume(now));
    }

    @Override
    public void revokeToken(UUID tokenId, String reason, Instant now) {
        tokenRepository.findById(tokenId).ifPresent(token -> token.revoke(reason, now));
    }

    @Override
    public void incrementFailedAttempt(UUID tokenId) {
        tokenRepository.findById(tokenId)
                .ifPresent(AuthActionTokenEntity::incrementFailedAttempt);
    }

    @Override
    public void revokeOpenTokens(
            UUID userId,
            String type,
            String reason,
            Instant now
    ) {
        tokenRepository.revokeOpenTokens(userId, type, reason, now);
    }

    @Override
    public boolean existsByTokenHash(String tokenHash) {
        return tokenRepository.existsByTokenHash(tokenHash);
    }

    @Override
    public long countByUserIdAndTypeSince(UUID userId, String type, Instant since) {
        return tokenRepository.countByUser_IdAndTypeAndCreatedAtGreaterThanEqual(
                userId,
                type,
                since
        );
    }

    @Override
    public Optional<Instant> findFirstCreatedAtByUserIdAndTypeSince(
            UUID userId,
            String type,
            Instant since
    ) {
        return tokenRepository
                .findFirstByUser_IdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        userId,
                        type,
                        since
                )
                .map(AuthActionTokenEntity::createdAt);
    }

    @Override
    public Optional<Instant> findLatestCreatedAtByUserIdAndType(UUID userId, String type) {
        return tokenRepository.findTopByUser_IdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(AuthActionTokenEntity::createdAt);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }
}
