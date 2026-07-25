package com.blaie.blaie_be.authz.application;

import com.blaie.blaie_be.core.security.CurrentUser;
import java.util.Set;

public interface UserPermissionResolver {
    Set<String> resolve(CurrentUser identity);
}
