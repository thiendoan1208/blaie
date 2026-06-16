package com.blaie.blaie_be.authz;

public interface AuthorizationService {
    void require(PermissionAction action);

    void require(PermissionAction action, OwnedResource resource);

    boolean can(PermissionAction action);

    boolean can(PermissionAction action, OwnedResource resource);
}
