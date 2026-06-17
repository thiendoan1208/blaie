package com.blaie.blaie_be.core.error;

import com.blaie.blaie_be.core.request.RequestContextHolder;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException exception, WebRequest request) {
        return buildResponse(exception.errorCode(), exception.getMessage(), exception.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception, WebRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>()).add(fieldError.getDefaultMessage());
        }
        return buildResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception, WebRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.computeIfAbsent(violation.getPropertyPath().toString(), key -> new ArrayList<>())
                        .add(violation.getMessage()));
        return buildResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        ErrorCode errorCode = switch (status) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case UNPROCESSABLE_CONTENT -> ErrorCode.VALIDATION_ERROR;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
        return buildResponse(errorCode, exception.getReason(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception exception, WebRequest request) {
        log.error("Unhandled exception", exception);
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            Map<String, List<String>> errors
    ) {
        String requestId = RequestContextHolder.currentRequestId().orElse(null);
        if (errorCode.status().is5xxServerError()) {
            log.error("requestId={} status={} message={}", requestId, errorCode.status(), message);
        } else {
            log.warn("requestId={} status={} message={}", requestId, errorCode.status(), message);
        }
        ApiErrorResponse body = ApiErrorResponse.of(errorCode, message, errors, requestId);
        return ResponseEntity.status(errorCode.status())
                .header("X-Request-ID", requestId == null ? "" : requestId)
                .body(body);
    }
}
