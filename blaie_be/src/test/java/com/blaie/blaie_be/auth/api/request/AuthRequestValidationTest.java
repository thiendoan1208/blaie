package com.blaie.blaie_be.auth.api.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class AuthRequestValidationTest {
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void trimsAndAcceptsValidRegistrationValues() {
        RegisterLocalRequest request = new RegisterLocalRequest(
                " valid.user ",
                " user@example.com ",
                " Nguyễn Văn An ",
                " Password1! "
        );

        Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
        Assertions.assertThat(request.username()).isEqualTo("valid.user");
        Assertions.assertThat(request.email()).isEqualTo("user@example.com");
        Assertions.assertThat(request.displayName()).isEqualTo("Nguyễn Văn An");
        Assertions.assertThat(request.password()).isEqualTo("Password1!");
    }

    @Test
    void acceptsPasswordsAtBothLengthBoundaries() {
        List<String> passwords = List.of("Upper!aa", "Uppercase!123456");

        for (String password : passwords) {
            RegisterLocalRequest request = new RegisterLocalRequest(
                    "valid.user",
                    "user@example.com",
                    "Display Name",
                    password
            );
            Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsInvalidPasswordLengthAndComposition() {
        List<String> passwords = List.of(
                "Upper!a",
                "Uppercase!1234567",
                "lowercase!1",
                "Password12"
        );

        for (String password : passwords) {
            RegisterLocalRequest request = new RegisterLocalRequest(
                    "valid.user",
                    "user@example.com",
                    "Display Name",
                    password
            );
            Assertions.assertThat(VALIDATOR.validate(request))
                    .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("password"));
        }
    }

    @Test
    void loginRequestAcceptsAnyNotBlankPassword() {
        List<String> passwords = List.of(
                "simple",
                "123",
                "PasswordWithDifferentPatternButNoUppercaseOrSpecialCharacters"
        );

        for (String password : passwords) {
            LoginLocalRequest request = new LoginLocalRequest("valid.user", password);
            Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
        }
    }

    @Test
    void loginRequestRejectsBlankPassword() {
        List<String> blankPasswords = List.of("", "   ");

        for (String password : blankPasswords) {
            LoginLocalRequest request = new LoginLocalRequest("valid.user", password);
            Assertions.assertThat(VALIDATOR.validate(request))
                    .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("password"));
        }
    }

    @Test
    void rejectsDisplayNamesOver32CharactersOrWithUnsupportedSymbols() {
        RegisterLocalRequest tooLong = validRegistration("A".repeat(33));
        RegisterLocalRequest unsupportedSymbol = validRegistration("Blaie 🚀");

        Assertions.assertThat(VALIDATOR.validate(tooLong))
                .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("displayName"));
        Assertions.assertThat(VALIDATOR.validate(unsupportedSymbol))
                .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("displayName"));
    }

    @Test
    void passwordResetRequestsTrimAndValidateInput() {
        PasswordResetRequest request = new PasswordResetRequest(" user@example.com ");
        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest(
                " user@example.com ",
                " 123456 ",
                " Password2@ "
        );

        Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
        Assertions.assertThat(VALIDATOR.validate(confirm)).isEmpty();
        Assertions.assertThat(request.email()).isEqualTo("user@example.com");
        Assertions.assertThat(confirm.email()).isEqualTo("user@example.com");
        Assertions.assertThat(confirm.code()).isEqualTo("123456");
        Assertions.assertThat(confirm.newPassword()).isEqualTo("Password2@");
    }

    @Test
    void passwordResetConfirmRejectsInvalidCodeAndPassword() {
        PasswordResetConfirmRequest badCode = new PasswordResetConfirmRequest(
                "user@example.com",
                "12345",
                "Password2@"
        );
        PasswordResetConfirmRequest badPassword = new PasswordResetConfirmRequest(
                "user@example.com",
                "123456",
                "password"
        );

        Assertions.assertThat(VALIDATOR.validate(badCode))
                .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("code"));
        Assertions.assertThat(VALIDATOR.validate(badPassword))
                .anySatisfy(violation -> Assertions.assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("newPassword"));
    }

    private RegisterLocalRequest validRegistration(String displayName) {
        return new RegisterLocalRequest(
                "valid.user",
                "user@example.com",
                displayName,
                "Password1!"
        );
    }
}
