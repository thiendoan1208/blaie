package com.blaie.blaie_be.auth.infrastructure.email;

import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "blaie.email", name = "provider", havingValue = "resend")
public class ResendEmailSenderAdapter implements EmailSenderPort {
    private final EmailProperties emailProperties;
    private final RestClient restClient;

    public ResendEmailSenderAdapter(EmailProperties emailProperties, RestClient.Builder restClientBuilder) {
        this.emailProperties = emailProperties;
        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + emailProperties.resendApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            restClient.post()
                    .uri("/emails")
                    .body(Map.of(
                            "from", emailProperties.from(),
                            "to", List.of(message.to()),
                            "subject", message.subject(),
                            "text", message.text(),
                            "html", message.html()
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Unable to send email");
        }
    }
}
