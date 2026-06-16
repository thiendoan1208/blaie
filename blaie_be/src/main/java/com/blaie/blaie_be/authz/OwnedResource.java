package com.blaie.blaie_be.authz;

public interface OwnedResource {
    String ownerId();

    default String tenantId() {
        return null;
    }
}
