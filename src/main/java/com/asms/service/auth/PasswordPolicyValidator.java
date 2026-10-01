package com.asms.service.auth;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Password policy of BR-AUTH-01: 8 to 64 characters, at least one letter and one digit, must not contain the part of
 * the email before "@" when that part has 4 or more characters, must not start or end with whitespace.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
public class PasswordPolicyValidator {

    public static final String VIOLATIONS = "violations";

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;
    private static final int MIN_EMAIL_NAME_LENGTH = 4;

    /** @return the unmet criteria, empty when the password is acceptable */
    public List<PasswordViolation> findViolations(String password, String email) {
        List<PasswordViolation> violations = new ArrayList<>();
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            violations.add(PasswordViolation.LENGTH);
        }
        if (password.codePoints().noneMatch(Character::isLetter)) {
            violations.add(PasswordViolation.LETTER);
        }
        if (password.codePoints().noneMatch(Character::isDigit)) {
            violations.add(PasswordViolation.DIGIT);
        }
        if (containsEmailName(password, email)) {
            violations.add(PasswordViolation.CONTAINS_EMAIL);
        }
        if (!password.isEmpty() && !password.equals(password.strip())) {
            violations.add(PasswordViolation.WHITESPACE);
        }
        return violations;
    }

    /** @throws BusinessException {@code AUTH_PASSWORD_POLICY} with the list of {@code violations} */
    public void check(String password, String email) {
        List<PasswordViolation> violations = findViolations(password, email);
        if (!violations.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AUTH_PASSWORD_POLICY,
                    Map.of(VIOLATIONS, violations.stream().map(Enum::name).toList()));
        }
    }

    private static boolean containsEmailName(String password, String email) {
        int at = email.indexOf('@');
        String name = at < 0 ? email : email.substring(0, at);
        return name.length() >= MIN_EMAIL_NAME_LENGTH
                && password.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }
}
