package com.asms.exception;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 Problem Details with the fields shared by every error response: {@code code}, {@code traceId} and
 * optional extra properties (Auth specification section 7.7).
 *
 * <p>Used by {@link GlobalExceptionHandler} and by the security entry points, which run outside Spring MVC.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class ProblemDetailFactory {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";
    public static final String TRACE_ID = "traceId";

    // Same key as com.asms.config.TraceIdFilter.MDC_KEY; duplicated because exception must not depend on config
    private static final String MDC_TRACE_ID = "traceId";

    private ProblemDetailFactory() {}

    public static ProblemDetail create(ErrorCode code) {
        return create(code, code.getDefaultMessage(), Map.of());
    }

    public static ProblemDetail create(ErrorCode code, String detail, Map<String, Object> properties) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        pd.setTitle(code.getStatus().getReasonPhrase());
        pd.setProperty(CODE, code.name());
        properties.forEach(pd::setProperty);
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId != null) {
            pd.setProperty(TRACE_ID, traceId);
        }
        return pd;
    }
}
