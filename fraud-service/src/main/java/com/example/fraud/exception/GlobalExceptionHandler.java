package com.example.fraud.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String error, String message, List<String> fields) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField())
                .distinct()
                .sorted()
                .toList();
        List<String> globals = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> e.getObjectName())
                .distinct()
                .toList();
        List<String> all = fields.isEmpty() ? globals : fields;
        return badRequest("Validation failed for: " + String.join(", ", all), all);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraint(ConstraintViolationException ex) {
        List<String> fields = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString())
                .distinct()
                .sorted()
                .toList();
        return badRequest("Validation failed for: " + String.join(", ", fields), fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Request body is missing or malformed", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatus s = HttpStatus.valueOf(er.getStatusCode().value());
            return ResponseEntity.status(s).body(new ErrorBody(
                    Instant.now(), s.value(), s.getReasonPhrase(), s.getReasonPhrase(), List.of()));
        }
        log.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(new ErrorBody(
                Instant.now(), status.value(), status.getReasonPhrase(), "Internal server error", List.of()));
    }

    private ResponseEntity<ErrorBody> badRequest(String message, List<String> fields) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorBody(
                Instant.now(), status.value(), status.getReasonPhrase(), message, fields));
    }
}
