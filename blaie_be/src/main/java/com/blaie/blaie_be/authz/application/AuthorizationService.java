package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.authz.domain.OwnedResource;
import com.blaie.blaie_be.authz.domain.PermissionAction;

public interface AuthorizationService {
    void require(PermissionAction action);

    void require(PermissionAction action, OwnedResource resource);

    boolean can(PermissionAction action);

    boolean can(PermissionAction action, OwnedResource resource);
}
