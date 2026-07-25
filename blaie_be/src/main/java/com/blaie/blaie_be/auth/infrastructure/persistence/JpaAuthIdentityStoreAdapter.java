package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthIdentityView;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthIdentityStoreAdapter implements AuthIdentityStorePort {
    private final UserRepository userRepository;
    private final AuthIdentityRepository identityRepository;

    public JpaAuthIdentityStoreAdapter(
            UserRepository userRepository,
            AuthIdentityRepository identityRepository
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Override
    public void createLocalIdentity(UUID userId, String passwordHash) {
        identityRepository.save(AuthIdentityEntity.local(requireUser(userId), passwordHash));
    }

    @Override
    public void createGoogleIdentity(UUID userId, String providerSubject) {
        identityRepository.saveAndFlush(
                AuthIdentityEntity.google(requireUser(userId), providerSubject)
        );
    }

    @Override
    public Optional<AuthIdentityView> findSingleLocalIdentityByIdentifier(
            String identifierNormalized
    ) {
        List<AuthIdentityEntity> identities = identityRepository.findAllByProviderAndIdentifier(
                AuthConstants.PROVIDER_LOCAL,
                identifierNormalized
        );
        return identities.size() == 1
                ? Optional.of(AuthPersistenceMapper.toIdentityView(identities.getFirst()))
                : Optional.empty();
    }

    @Override
    public Optional<AuthUserView> findGoogleUserBySubject(String providerSubject) {
        return identityRepository
                .findByProviderAndProviderSubject(AuthConstants.PROVIDER_GOOGLE, providerSubject)
                .map(AuthIdentityEntity::user)
                .map(AuthPersistenceMapper::toUserView);
    }

    @Override
    public Optional<AuthIdentityView> findLocalIdentity(UUID userId) {
        return identityRepository.findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .map(AuthPersistenceMapper::toIdentityView);
    }

    @Override
    public boolean existsVerifiedEmail(UUID userId) {
        return identityRepository.existsByUser_IdAndEmailVerifiedTrue(userId);
    }

    @Override
    public void markLocalEmailVerified(UUID userId) {
        AuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));
        identity.markEmailVerified();
    }

    @Override
    public void updateLocalPasswordHash(UUID userId, String passwordHash) {
        identityRepository.findByUser_IdAndProvider(userId, AuthConstants.PROVIDER_LOCAL)
                .ifPresentOrElse(
                        identity -> identity.updatePasswordHash(passwordHash),
                        () -> identityRepository.save(
                                AuthIdentityEntity.local(requireUser(userId), passwordHash)
                        )
                );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }
}
