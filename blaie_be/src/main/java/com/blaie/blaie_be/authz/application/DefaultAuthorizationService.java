package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.authz.domain.OwnedResource;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuthorizationService implements AuthorizationService {
    @Override
    public void require(PermissionAction action) {
        if (!can(action)) {
            throw unauthorizedOrForbidden();
        }
    }

    @Override
    public void require(PermissionAction action, OwnedResource resource) {
        if (!can(action, resource)) {
            throw unauthorizedOrForbidden();
        }
    }

    @Override
    public boolean can(PermissionAction action) {
        CurrentUser currentUser = CurrentUserHolder.current().orElse(null);
        return currentUser != null && currentUser.hasPermission(action.key());
    }

    @Override
    public boolean can(PermissionAction action, OwnedResource resource) {
        CurrentUser currentUser = CurrentUserHolder.current().orElse(null);
        if (currentUser == null) {
            return false;
        }
        if (currentUser.admin()) {
            return true;
        }
        return resource != null
                && currentUser.hasPermission(action.key())
                && Objects.equals(currentUser.userId(), resource.ownerId());
    }

    private AppException unauthorizedOrForbidden() {
        if (CurrentUserHolder.current().isEmpty()) {
            return new AppException(ErrorCode.UNAUTHORIZED);
        }
        return new AppException(ErrorCode.FORBIDDEN);
    }
}
