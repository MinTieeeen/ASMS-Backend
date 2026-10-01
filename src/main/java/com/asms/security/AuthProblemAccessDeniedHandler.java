package com.asms.security;

import com.asms.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers authenticated requests without the required system role (for example {@code /api/v1/admin/**} for a
 * {@code USER}) with a 403 {@code AUTH_FORBIDDEN} Problem Details body (BR-AUTH-15).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class AuthProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(response, ErrorCode.AUTH_FORBIDDEN);
    }
}
