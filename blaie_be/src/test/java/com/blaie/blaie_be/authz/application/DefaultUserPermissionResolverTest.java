package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.security.CurrentUser;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUserPermissionResolverTest {
    private final DefaultUserPermissionResolver resolver = new DefaultUserPermissionResolver();

    @Test
    void regularUserReceivesOnlyPermissionsForExistingUserEndpoints() {
        Set<String> permissions = resolver.resolve(new CurrentUser("user-1", false, Set.of()));

        assertThat(permissions).containsExactlyInAnyOrder(
                PermissionAction.CAPTURE_CREATE.key(),
                PermissionAction.CAPTURE_READ.key(),
                PermissionAction.CAPTURE_UPDATE.key(),
                PermissionAction.CAPTURE_DELETE.key(),
                PermissionAction.INBOX_READ.key(),
                PermissionAction.ITEM_READ.key()
        );
        assertThat(permissions).doesNotContain(
                PermissionAction.ITEM_UPDATE.key(),
                PermissionAction.ADMIN_CAPTURE_JOB_READ.key()
        );
    }

    @Test
    void explicitPermissionsArePreservedWithoutAssigningStandardUserPermissionsToAdmin() {
        assertThat(resolver.resolve(new CurrentUser(
                "user-1",
                false,
                Set.of(PermissionAction.TASK_READ.key())
        ))).contains(PermissionAction.TASK_READ.key(), PermissionAction.CAPTURE_READ.key());

        assertThat(resolver.resolve(new CurrentUser(
                "admin-1",
                true,
                Set.of(PermissionAction.ADMIN_AUDIT_READ.key())
        ))).containsExactly(PermissionAction.ADMIN_AUDIT_READ.key());
    }
}
