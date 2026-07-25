package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.auth.application.port.AuthActionTokenStorePort;
import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenStorePort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStoreAdapterBoundaryTest {
    @Test
    void eachAuthPersistenceAdapterImplementsExactlyOneApplicationPort() {
        assertThat(JpaAuthUserStoreAdapter.class.getInterfaces())
                .containsExactly(AuthUserStorePort.class);
        assertThat(JpaAuthIdentityStoreAdapter.class.getInterfaces())
                .containsExactly(AuthIdentityStorePort.class);
        assertThat(JpaRefreshTokenStoreAdapter.class.getInterfaces())
                .containsExactly(RefreshTokenStorePort.class);
        assertThat(JpaAuthActionTokenStoreAdapter.class.getInterfaces())
                .containsExactly(AuthActionTokenStorePort.class);
    }
}
