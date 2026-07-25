package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.authz.domain.OwnedResource;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationServiceTest {
    private final DefaultAuthorizationService service = new DefaultAuthorizationService();

    @AfterEach
    void clearContext() {
        CurrentUserHolder.clear();
    }

    @Test
    void capabilityCheckRequiresResolvedPermissionForRegularUser() {
        CurrentUserHolder.set(user("user-1", PermissionAction.CAPTURE_READ));

        assertThat(service.can(PermissionAction.CAPTURE_READ)).isTrue();
        assertThat(service.can(PermissionAction.CAPTURE_DELETE)).isFalse();
    }

    @Test
    void resourceCheckRequiresBothPermissionAndOwnership() {
        CurrentUserHolder.set(user("user-1", PermissionAction.CAPTURE_READ));

        assertThat(service.can(PermissionAction.CAPTURE_READ, ownedBy("user-1"))).isTrue();
        assertThat(service.can(PermissionAction.CAPTURE_READ, ownedBy("user-2"))).isFalse();
        assertThat(service.can(PermissionAction.CAPTURE_DELETE, ownedBy("user-1"))).isFalse();
    }

    @Test
    void adminBypassesPermissionAndOwnershipWhileAnonymousIsDenied() {
        CurrentUserHolder.set(new CurrentUser("admin-1", true, Set.of()));
        assertThat(service.can(PermissionAction.CAPTURE_DELETE, ownedBy("user-2"))).isTrue();

        CurrentUserHolder.clear();
        assertThat(service.can(PermissionAction.CAPTURE_READ)).isFalse();
        assertThatThrownBy(() -> service.require(PermissionAction.CAPTURE_READ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private CurrentUser user(String id, PermissionAction... actions) {
        return new CurrentUser(
                id,
                false,
                java.util.Arrays.stream(actions).map(PermissionAction::key).collect(
                        java.util.stream.Collectors.toUnmodifiableSet()
                )
        );
    }

    private OwnedResource ownedBy(String ownerId) {
        return () -> ownerId;
    }
}
