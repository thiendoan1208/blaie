package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class AuthPersistenceExceptionTranslator {
    private AuthPersistenceExceptionTranslator() {
    }

    public static RuntimeException translateRegistrationDuplicate(DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);
        if (constraintName == null) {
            return exception;
        }
        return switch (constraintName.toLowerCase(Locale.ROOT)) {
            case "uq_users_username_normalized" ->
                    new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            case "uq_users_email_normalized" ->
                    new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            default -> exception;
        };
    }

    private static String findConstraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
