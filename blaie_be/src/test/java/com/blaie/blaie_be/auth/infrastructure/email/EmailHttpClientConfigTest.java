package com.blaie.blaie_be.auth.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHttpClientConfigTest {

    @Test
    void givesEveryProviderAnIndependentRestClientBuilder() {
        try (var context = new AnnotationConfigApplicationContext(EmailHttpClientConfig.class)) {
            RestClient.Builder firstProvider = context.getBean(RestClient.Builder.class);
            RestClient.Builder secondProvider = context.getBean(RestClient.Builder.class);

            assertThat(firstProvider).isNotSameAs(secondProvider);
        }
    }
}
