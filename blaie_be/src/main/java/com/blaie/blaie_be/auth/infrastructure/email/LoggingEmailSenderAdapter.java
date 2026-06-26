package com.blaie.blaie_be.auth.infrastructure.email;

import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "blaie.email", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSenderAdapter implements EmailSenderPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderAdapter.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email provider=log to={} subject={} text={}", message.to(), message.subject(), message.text());
    }
}
