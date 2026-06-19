package com.blaie.blaie_be.auth.infrastructure.persistence;

import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.sql.SQLException;
import org.assertj.core.api.Assertions;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuthPersistenceExceptionTranslatorTest {
    @Test
    void usernameConstraintsMapToUsernameConflict() {
        RuntimeException translated = AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(
                violation("uq_users_username_normalized")
        );

        Assertions.assertThat(translated)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));
    }

    @Test
    void emailConstraintsMapToEmailConflict() {
        RuntimeException translated = AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(
                violation("uq_auth_identities_local_email_normalized")
        );

        Assertions.assertThat(translated)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void unknownConstraintPreservesOriginalException() {
        DataIntegrityViolationException original = violation("fk_unrelated_constraint");

        Assertions.assertThat(AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(original))
                .isSameAs(original);
    }

    private DataIntegrityViolationException violation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate",
                new SQLException("duplicate"),
                "insert into users",
                constraintName
        );
        return new DataIntegrityViolationException("duplicate", cause);
    }
}
