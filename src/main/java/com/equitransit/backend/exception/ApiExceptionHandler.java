/*
 * ApiExceptionHandler
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Translate exceptions to safe API errors.
 */
package com.equitransit.backend.exception;


import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.
        MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;


/** Returns consistent errors without exposing secrets or SQL details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Returns safe lifecycle errors with stable frontend domain codes.
     * @param error the expected request failure
     * @return the safe response without owner or SQL details
     */
    @ExceptionHandler(OrderException.class)
    public ResponseEntity<Map<String, Object>> handleOrder(
            OrderException error) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("code", error.getCode());
        body.put("message", error.getMessage());
        return ResponseEntity.status(error.getStatus()).body(body);
    }

    /** @param error oversized multipart request @return stable size error */
    @ExceptionHandler(org.springframework.web.multipart
            .MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException
                    error) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                Map.of("code", "FILE_SIZE_INVALID", "message",
                        "Uploads must not exceed 10 MiB."));
    }

    /**
     * Returns a stable horse-domain code without SQL or owner details.
     * @param error the expected domain failure
     * @return the safe response
     */
    @ExceptionHandler(HorseException.class)
    public ResponseEntity<Map<String, Object>> handleHorse(
            HorseException error) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("code", error.getCode());
        body.put("message", error.getMessage());
        return ResponseEntity.status(error.getStatus()).body(body);
    }

    /**
     * Rejects invalid path/query values without reflecting submitted data.
     * @param error the invalid parameter
     * @return the safe bad-request response
     */
    @ExceptionHandler({ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleParameter(
            Exception error) {
        return response(HttpStatus.BAD_REQUEST,
                "Invalid request parameters.");
    }

    /**
     * Rejects unsupported mutations, including horse deletion.
     * @param error the unsupported HTTP method
     * @return the method-not-allowed response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(
            HttpRequestMethodNotSupportedException error) {
        return response(HttpStatus.METHOD_NOT_ALLOWED,
                "This method is not supported.");
    }

    /**
     * Returns a safe response for service-level authorization failures.
     * @param error the denied access
     * @return the forbidden response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException error) {
        return response(HttpStatus.FORBIDDEN, "Access is denied.");
    }

    /**
     * Returns a safe response when a current domain identity is unavailable.
     * @param error the unavailable authentication
     * @return the unauthorized response
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(
            AuthenticationException error) {
        return response(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    /**
     * Handles an expected business error.
     * @param error the business error
     * @return the safe error response
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException error) {
        if (error instanceof AuthThrottleException throttle) {
            return ResponseEntity.status(error.getStatus())
                    .header("Retry-After", Long.toString(throttle.retryAfter()))
                    .body(Map.of("code", error.getCode(),
                            "message", error.getMessage()));
        }
        if (error.getCode() != null) {
            return ResponseEntity.status(error.getStatus()).body(Map.of(
                    "code", error.getCode(), "message", error.getMessage()));
        }
        return response(error.getStatus(), error.getMessage());
    }

    /**
     * Reports invalid field names without echoing submitted passwords.
     * @param error the validation error
     * @return the validation response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException error) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();

        error.getBindingResult().getFieldErrors().forEach(item ->
                fields.put(item.getField(), item.getDefaultMessage()));
        body.put("message", "Request validation failed.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Rejects malformed JSON and unexpected input fields.
     * @param error the parsing error
     * @return a safe bad-request response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJson(
            HttpMessageNotReadableException error) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request body.");
    }

    /**
     * Handles a concurrent uniqueness conflict.
     * @param error the persistence conflict
     * @return the safe conflict response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            DataIntegrityViolationException error) {
        return response(HttpStatus.CONFLICT,
                "The request conflicts with an existing record.");
    }

    /**
     * Handles an unexpected error without leaking implementation details.
     * @param error the unexpected failure
     * @return the safe server-error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception error) {
        LOGGER.error("Unexpected API failure: {}",
                error.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "The request could not be completed.");
    }

    private ResponseEntity<Map<String, Object>> response(
            HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
