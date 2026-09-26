package com.asms.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("BR-AUTH-01: password policy")
class PasswordPolicyValidatorTest {

    private static final String EMAIL = "nguyen.van.a@gmail.com";

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void findViolations_shouldBeEmpty_whenPasswordValid() {
        assertThat(validator.findViolations("Secret123", EMAIL)).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Abc1234, LENGTH",
        "12345678, LETTER",
        "abcdefgh, DIGIT",
        "' Secret123', WHITESPACE",
        "'Secret123 ', WHITESPACE",
        "xNguyen.Van.A9, CONTAINS_EMAIL",
    })
    void findViolations_shouldReportRule(String password, PasswordViolation expected) {
        assertThat(validator.findViolations(password, EMAIL)).contains(expected);
    }

    @Test
    void findViolations_shouldAcceptLengthBounds() {
        assertThat(validator.findViolations("a1" + "x".repeat(6), EMAIL)).isEmpty();
        assertThat(validator.findViolations("a1" + "x".repeat(62), EMAIL)).isEmpty();
        assertThat(validator.findViolations("a1" + "x".repeat(63), EMAIL)).containsExactly(PasswordViolation.LENGTH);
    }

    @Test
    void findViolations_shouldIgnoreEmailName_whenShorterThanFourCharacters() {
        assertThat(validator.findViolations("abc12345", "abc@gmail.com")).isEmpty();
    }

    @Test
    void findViolations_shouldAcceptVietnameseLetters() {
        assertThat(validator.findViolations("Mậtkhẩu9", EMAIL)).isEmpty();
    }

    @Test
    void check_shouldThrowWithViolations() {
        assertThatThrownBy(() -> validator.check("abc", EMAIL)).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_PASSWORD_POLICY);
            assertThat(e.getProperties()).containsEntry(PasswordPolicyValidator.VIOLATIONS, List.of("LENGTH", "DIGIT"));
        });
    }
}
