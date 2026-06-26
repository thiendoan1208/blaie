package com.blaie.blaie_be.auth.application.port;

public interface EmailSenderPort {
    void send(EmailMessage message);
}
