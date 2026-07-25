package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthUserStoreAdapter implements AuthUserStorePort {
    private final UserRepository userRepository;

    public JpaAuthUserStoreAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    public AuthUserView createLocalUser(
            String username,
            String usernameNormalized,
            String email,
            String emailNormalized,
            String displayName
    ) {
        try {
            return AuthPersistenceMapper.toUserView(userRepository.saveAndFlush(
                    UserEntity.localUser(
                            username,
                            usernameNormalized,
                            email,
                            emailNormalized,
                            displayName
                    )
            ));
        } catch (DataIntegrityViolationException exception) {
            throw AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(exception);
        }
    }

    @Override
    public AuthUserView createGoogleUser(
            String email,
            String emailNormalized,
            String displayName,
            String avatarUrl
    ) {
        return AuthPersistenceMapper.toUserView(userRepository.saveAndFlush(
                UserEntity.googleUser(email, emailNormalized, displayName, avatarUrl)
        ));
    }

    @Override
    public Optional<AuthUserView> findByEmailNormalized(String emailNormalized) {
        return userRepository.findByEmailNormalized(emailNormalized)
                .map(AuthPersistenceMapper::toUserView);
    }

    @Override
    public Optional<AuthUserView> findActiveById(UUID userId) {
        return userRepository.findByIdAndStatus(userId, AuthConstants.USER_STATUS_ACTIVE)
                .map(AuthPersistenceMapper::toUserView);
    }

    @Override
    public Optional<AuthUserView> findByUsernameNormalized(String usernameNormalized) {
        return userRepository.findByUsernameNormalized(usernameNormalized)
                .map(AuthPersistenceMapper::toUserView);
    }

    @Override
    public void updateUsername(UUID userId, String username, String usernameNormalized) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        user.updateUsername(username, usernameNormalized);
    }
}
