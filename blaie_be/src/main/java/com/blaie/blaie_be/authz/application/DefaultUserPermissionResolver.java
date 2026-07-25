package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.security.CurrentUser;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DefaultUserPermissionResolver implements UserPermissionResolver {
    private static final Set<PermissionAction> STANDARD_USER_ACTIONS = Set.of(
            PermissionAction.CAPTURE_CREATE,
            PermissionAction.CAPTURE_READ,
            PermissionAction.CAPTURE_UPDATE,
            PermissionAction.CAPTURE_DELETE,
            PermissionAction.INBOX_READ,
            PermissionAction.ITEM_READ
    );

    @Override
    public Set<String> resolve(CurrentUser identity) {
        HashSet<String> permissions = new HashSet<>(identity.permissions());
        if (!identity.admin()) {
            STANDARD_USER_ACTIONS.stream()
                    .map(PermissionAction::key)
                    .forEach(permissions::add);
        }
        return Set.copyOf(permissions);
    }
}
