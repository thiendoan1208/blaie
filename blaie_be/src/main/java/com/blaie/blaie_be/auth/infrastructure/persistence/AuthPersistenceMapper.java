package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthActionTokenView;
import com.blaie.blaie_be.auth.application.port.AuthIdentityView;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.application.port.RefreshTokenView;

final class AuthPersistenceMapper {
    private AuthPersistenceMapper() {
    }

    static AuthUserView toUserView(UserEntity user) {
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

    static AuthIdentityView toIdentityView(AuthIdentityEntity identity) {
        return new AuthIdentityView(
                toUserView(identity.user()),
                identity.provider(),
                identity.providerSubject(),
                identity.emailVerified(),
                identity.passwordHash()
        );
    }

    static RefreshTokenView toRefreshTokenView(RefreshTokenEntity token) {
        return new RefreshTokenView(
                toUserView(token.user()),
                token.tokenFamilyId(),
                token.expiresAt(),
                token.isRevoked()
        );
    }

    static AuthActionTokenView toActionTokenView(AuthActionTokenEntity token) {
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
