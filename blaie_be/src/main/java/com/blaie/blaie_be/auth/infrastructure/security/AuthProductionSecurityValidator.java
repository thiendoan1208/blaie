package com.blaie.blaie_be.auth.infrastructure.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class AuthProductionSecurityValidator implements InitializingBean {
    private final AuthProperties authProperties;
    private final Environment environment;

    public AuthProductionSecurityValidator(AuthProperties authProperties, Environment environment) {
        this.authProperties = authProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !authProperties.cookieSecure()) {
            throw new IllegalStateException("Production profile requires secure auth cookies");
        }
    }
}
