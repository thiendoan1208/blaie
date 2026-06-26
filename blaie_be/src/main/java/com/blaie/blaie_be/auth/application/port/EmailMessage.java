package com.blaie.blaie_be.auth.application.port;

public record EmailMessage(
        String to,
        String subject,
        String text,
        String html
) {
}
