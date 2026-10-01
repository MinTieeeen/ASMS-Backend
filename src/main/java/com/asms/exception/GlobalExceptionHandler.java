package com.asms.exception;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every exception into an RFC 9457 Problem Details response with a {@code code} field (business error code),
 * a {@code traceId}, optional extra properties and, for validation errors, an {@code errors} list of
 * {@code {field, code, message}} (Auth specification section 7.7).
 *
 * @author MinhTien
 * @version 2.1.0
 * @since 2026-09-26
 * @modified 2026-09-27
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * One invalid field. {@code code} is machine readable ({@code REQUIRED}, {@code INVALID_FORMAT},
     * {@code INVALID_LENGTH}, {@code OUT_OF_RANGE}, {@code INVALID}); {@code message} is a fallback for humans.
     */
    public record FieldError(
            String field, String code, @Nullable String message) {}

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        ProblemDetail body = ProblemDetailFactory.create(code, ex.getMessage(), ex.getProperties());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(code.getStatus());
        if (code.getStatus() == HttpStatus.TOO_MANY_REQUESTS
                && ex.getProperties().get(BusinessException.RETRY_AFTER_SECONDS) instanceof Number seconds) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return response.body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetailFactory.create(ErrorCode.AUTH_FORBIDDEN);
    }

    /** Sorting a page by an unknown property ({@code ?sort=unknown}) is a client error, not a server error. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return ProblemDetailFactory.create(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                Map.of(ProblemDetailFactory.ERRORS, List.of(new FieldError("sort", "INVALID", ex.getMessage()))));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetailFactory.create(ErrorCode.INTERNAL_ERROR);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), toFieldErrorCode(e.getCode()), e.getDefaultMessage()))
                .toList();
        ProblemDetail body = ProblemDetailFactory.create(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                Map.of(ProblemDetailFactory.ERRORS, errors));
        return ResponseEntity.badRequest().body(body);
    }

    /** Adds {@code code} and {@code traceId} to errors raised by Spring MVC itself (unreadable body, 404, 405...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = statusCode.value() == HttpStatus.NOT_FOUND.value()
                ? ErrorCode.RESOURCE_NOT_FOUND
                : statusCode.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = ProblemDetailFactory.create(code);
        problem.setStatus(statusCode.value());
        if (body instanceof ProblemDetail original) {
            problem.setTitle(original.getTitle());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private static String toFieldErrorCode(@Nullable String constraint) {
        if (constraint == null) {
            return "INVALID";
        }
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Size", "Length" -> "INVALID_LENGTH";
            case "Min", "Max", "Positive", "PositiveOrZero", "DecimalMin", "DecimalMax" -> "OUT_OF_RANGE";
            default -> "INVALID";
        };
    }
}
