package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthActionTokenStorePort;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenView;
import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthIdentityView;
import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.application.port.RefreshTokenStorePort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenView;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthStoreAdapter implements AuthUserStorePort, AuthIdentityStorePort, RefreshTokenStorePort, AuthActionTokenStorePort {
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthActionTokenRepository authActionTokenRepository;

    public JpaAuthStoreAdapter(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthActionTokenRepository authActionTokenRepository
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authActionTokenRepository = authActionTokenRepository;
    }

    @Override
    public boolean existsByUsernameNormalized(String usernameNormalized) {
        return userRepository.existsByUsernameNormalized(usernameNormalized);
    }

    @Override
    public boolean existsByEmailNormalized(String emailNormalized) {
        return userRepository.existsByEmailNormalized(emailNormalized);
    }

    @Override
    public AuthUserView createLocalUser(String username, String usernameNormalized, String email, String emailNormalized, String displayName) {
        try {
            return toUserView(userRepository.saveAndFlush(
                    UserEntity.localUser(username, usernameNormalized, email, emailNormalized, displayName)
            ));
        } catch (DataIntegrityViolationException exception) {
            throw AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(exception);
        }
    }

    @Override
    public AuthUserView createGoogleUser(String email, String emailNormalized, String displayName, String avatarUrl) {
        return toUserView(userRepository.saveAndFlush(UserEntity.googleUser(email, emailNormalized, displayName, avatarUrl)));
    }

    @Override
    public Optional<AuthUserView> findByEmailNormalized(String emailNormalized) {
        return userRepository.findByEmailNormalized(emailNormalized).map(this::toUserView);
    }

    @Override
    public Optional<AuthUserView> findActiveById(UUID userId) {
        return userRepository.findByIdAndStatus(userId, AuthConstants.USER_STATUS_ACTIVE).map(this::toUserView);
    }

    @Override
    public Optional<AuthUserView> findByUsernameNormalized(String usernameNormalized) {
        return userRepository.findByUsernameNormalized(usernameNormalized).map(this::toUserView);
    }

    @Override
    public void updateUsername(UUID userId, String username, String usernameNormalized) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        user.updateUsername(username, usernameNormalized);
    }

    @Override
    public void createLocalIdentity(UUID userId, String passwordHash) {
        authIdentityRepository.save(AuthIdentityEntity.local(requireUser(userId), passwordHash));
    }

    @Override
    public void createGoogleIdentity(UUID userId, String providerSubject) {
        authIdentityRepository.saveAndFlush(AuthIdentityEntity.google(requireUser(userId), providerSubject));
    }

    @Override
    public Optional<AuthIdentityView> findSingleLocalIdentityByIdentifier(String identifierNormalized) {
        List<AuthIdentityEntity> identities = authIdentityRepository.findAllByProviderAndIdentifier(
                AuthConstants.PROVIDER_LOCAL,
                identifierNormalized
        );
        return identities.size() == 1 ? Optional.of(toIdentityView(identities.getFirst())) : Optional.empty();
    }

    @Override
    public Optional<AuthUserView> findGoogleUserBySubject(String providerSubject) {
        return authIdentityRepository
                .findByProviderAndProviderSubject(AuthConstants.PROVIDER_GOOGLE, providerSubject)
                .map(AuthIdentityEntity::user)
                .map(this::toUserView);
    }

    @Override
    public Optional<AuthIdentityView> findLocalIdentity(UUID userId) {
        return authIdentityRepository.findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .map(this::toIdentityView);
    }

    @Override
    public boolean existsVerifiedEmail(UUID userId) {
        return authIdentityRepository.existsByUser_IdAndEmailVerifiedTrue(userId);
    }

    @Override
    public void markLocalEmailVerified(UUID userId) {
        AuthIdentityEntity localIdentity = authIdentityRepository
                .findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));
        localIdentity.markEmailVerified();
    }

    @Override
    public void updateLocalPasswordHash(UUID userId, String passwordHash) {
        authIdentityRepository.findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .ifPresentOrElse(
                        identity -> identity.updatePasswordHash(passwordHash),
                        () -> authIdentityRepository.save(AuthIdentityEntity.local(requireUser(userId), passwordHash))
                );
    }

    @Override
    public void createWebRefreshToken(UUID userId, String tokenHash, UUID tokenFamilyId, Instant expiresAt, String userAgent) {
        refreshTokenRepository.save(RefreshTokenEntity.webCookie(requireUser(userId), tokenHash, tokenFamilyId, expiresAt, userAgent));
    }

    @Override
    public Optional<RefreshTokenView> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash).map(this::toRefreshTokenView);
    }

    @Override
    public void rotateWebRefreshToken(String currentTokenHash, String nextTokenHash, Instant expiresAt, String userAgent, Instant now) {
        RefreshTokenEntity currentToken = refreshTokenRepository.findByTokenHash(currentTokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        RefreshTokenEntity nextRefreshToken = refreshTokenRepository.save(RefreshTokenEntity.webCookie(
                currentToken.user(),
                nextTokenHash,
                currentToken.tokenFamilyId(),
                expiresAt,
                userAgent
        ));
        currentToken.markUsed(now);
        currentToken.revoke("rotated", nextRefreshToken, now);
    }

    @Override
    public void revokeRefreshToken(String tokenHash, String reason, Instant now) {
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.markUsed(now);
                    token.revoke(reason, null, now);
                });
    }

    @Override
    public void revokeFamily(UUID tokenFamilyId, String reason, Instant now) {
        refreshTokenRepository.revokeFamily(tokenFamilyId, reason, now);
    }

    @Override
    public void revokeAllUserTokens(UUID userId, String reason, Instant now) {
        refreshTokenRepository.revokeAllUserTokens(userId, reason, now);
    }

    @Override
    public void createToken(UUID userId, String type, String tokenHash, Instant expiresAt) {
        authActionTokenRepository.save(AuthActionTokenEntity.create(requireUser(userId), type, tokenHash, expiresAt));
    }

    @Override
    public Optional<AuthActionTokenView> findByTokenHashAndType(String tokenHash, String type) {
        return authActionTokenRepository.findByTokenHashAndType(tokenHash, type).map(this::toActionTokenView);
    }

    @Override
    public Optional<AuthActionTokenView> findLatestPendingForUpdate(UUID userId, String type) {
        return authActionTokenRepository.findLatestPendingForUpdate(userId, type).map(this::toActionTokenView);
    }

    @Override
    public void consumeToken(UUID tokenId, Instant now) {
        authActionTokenRepository.findById(tokenId).ifPresent(token -> token.consume(now));
    }

    @Override
    public void revokeToken(UUID tokenId, String reason, Instant now) {
        authActionTokenRepository.findById(tokenId).ifPresent(token -> token.revoke(reason, now));
    }

    @Override
    public void incrementFailedAttempt(UUID tokenId) {
        authActionTokenRepository.findById(tokenId).ifPresent(AuthActionTokenEntity::incrementFailedAttempt);
    }

    @Override
    public void revokeOpenTokens(UUID userId, String type, String reason, Instant now) {
        authActionTokenRepository.revokeOpenTokens(userId, type, reason, now);
    }

    @Override
    public boolean existsByTokenHash(String tokenHash) {
        return authActionTokenRepository.existsByTokenHash(tokenHash);
    }

    @Override
    public long countByUserIdAndTypeSince(UUID userId, String type, Instant since) {
        return authActionTokenRepository.countByUser_IdAndTypeAndCreatedAtGreaterThanEqual(userId, type, since);
    }

    @Override
    public Optional<Instant> findFirstCreatedAtByUserIdAndTypeSince(UUID userId, String type, Instant since) {
        return authActionTokenRepository
                .findFirstByUser_IdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(userId, type, since)
                .map(AuthActionTokenEntity::createdAt);
    }

    @Override
    public Optional<Instant> findLatestCreatedAtByUserIdAndType(UUID userId, String type) {
        return authActionTokenRepository.findTopByUser_IdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(AuthActionTokenEntity::createdAt);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    private AuthUserView toUserView(UserEntity user) {
        return new AuthUserView(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                user.admin(),
                user.displayName(),
                user.avatarUrl(),
                user.createdAt()
        );
    }

    private AuthIdentityView toIdentityView(AuthIdentityEntity identity) {
        return new AuthIdentityView(
                toUserView(identity.user()),
                identity.provider(),
                identity.providerSubject(),
                identity.emailVerified(),
                identity.passwordHash()
        );
    }

    private RefreshTokenView toRefreshTokenView(RefreshTokenEntity token) {
        return new RefreshTokenView(
                toUserView(token.user()),
                token.tokenFamilyId(),
                token.expiresAt(),
                token.isRevoked()
        );
    }

    private AuthActionTokenView toActionTokenView(AuthActionTokenEntity token) {
        return new AuthActionTokenView(
                token.id(),
                token.user().id(),
                token.tokenHash(),
                token.expiresAt(),
                token.consumedAt(),
                token.revokedAt(),
                token.failedAttemptCount(),
                token.createdAt()
        );
    }
}
