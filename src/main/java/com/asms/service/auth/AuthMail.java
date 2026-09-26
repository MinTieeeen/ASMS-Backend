package com.asms.service.auth;

import com.asms.entity.user.Language;
import java.util.Map;

/**
 * One email to send, stored as the argument of a JobRunr job. {@code variables} are the template variables, already
 * formatted as text; {@code language} selects the translation (NFR14).
 *
 * <p>A record prints every field in {@code toString}, so never log an {@code AuthMail}: its link may carry a one-time
 * token (NFR-AUTH-03).
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public record AuthMail(AuthMailTemplate template, String to, Language language, Map<String, String> variables) {

    public AuthMail {
        variables = Map.copyOf(variables);
    }
}
