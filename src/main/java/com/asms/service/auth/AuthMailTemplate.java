package com.asms.service.auth;

import lombok.Getter;

/**
 * Emails of the Auth module (section 7.9). Each has an HTML and a plain-text Thymeleaf template in
 * {@code templates/email/{templateName}.html|.txt}; their texts live under {@code email.{messageKey}.*} in
 * {@link EmailMessages}.
 *
 * @author MinhTien
 * @version 2.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public enum AuthMailTemplate {
    WELCOME_ACTIVATION("welcome-activation", "welcomeActivation"),
    PASSWORD_RESET("password-reset", "passwordReset"),
    PASSWORD_CHANGED("password-changed", "passwordChanged");

    private final String templateName;
    private final String messageKey;

    AuthMailTemplate(String templateName, String messageKey) {
        this.templateName = templateName;
        this.messageKey = messageKey;
    }

    public String subjectKey() {
        return "email." + messageKey + ".subject";
    }
}
