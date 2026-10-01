package com.asms.security;

import com.asms.exception.ErrorCode;
import com.asms.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a Problem Details error directly to the servlet response. Used by security filters and entry points, which
 * run before Spring MVC and therefore cannot rely on {@code GlobalExceptionHandler}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        ProblemDetail problem = ProblemDetailFactory.create(code);
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), toBody(problem));
    }

    // Flattened by hand: the mixin that flattens ProblemDetail properties is registered only for Spring MVC converters
    private static Map<String, Object> toBody(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "type",
                problem.getType() == null ? "about:blank" : problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
