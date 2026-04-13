package com.civicworks.platform.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        String requestId = requestId(req);
        log.warn("Business error: code={} message={} requestId={}", ex.getCode(), ex.getMessage(), requestId);
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(ApiError.of(ex.getCode(), ex.getMessage(), requestId)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String requestId = requestId(req);
        BindingResult br = ex.getBindingResult();
        List<ApiError.FieldError> details = br.getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ApiError.withDetails(ErrorCode.VALIDATION_ERROR, "Validation failed", details, requestId)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                                 HttpServletRequest req) {
        String requestId = requestId(req);
        String headerName = ex.getHeaderName();
        String code = "Idempotency-Key".equalsIgnoreCase(headerName)
                ? ErrorCode.MISSING_IDEMPOTENCY_KEY
                : ErrorCode.VALIDATION_ERROR;
        String message = "Required header '" + headerName + "' is missing";
        log.warn("Missing header: {} requestId={}", headerName, requestId);
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ApiError.of(code, message, requestId)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest req) {
        String requestId = requestId(req);
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty() ? "?" :
                    ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String allowed = String.join(",",
                    java.util.Arrays.stream(ife.getTargetType().getEnumConstants())
                            .map(Object::toString).toList());
            ApiError.FieldError detail = new ApiError.FieldError(field,
                    "must be one of [" + allowed + "]");
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse(ApiError.withDetails(
                            ErrorCode.VALIDATION_ERROR,
                            "Invalid enum value for field '" + field + "'",
                            List.of(detail), requestId)));
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ApiError.of(ErrorCode.VALIDATION_ERROR,
                        "Malformed request body", requestId)));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        String requestId = requestId(req);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(ApiError.of(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                        "Resource was modified by another request", requestId)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        String requestId = requestId(req);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(ApiError.of(ErrorCode.ACCESS_DENIED, "Access denied", requestId)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex, HttpServletRequest req) {
        String requestId = requestId(req);
        log.error("Unhandled exception requestId={}", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(ApiError.of("INTERNAL_ERROR", "An internal error occurred", requestId)));
    }

    private String requestId(HttpServletRequest req) {
        Object id = req.getAttribute("requestId");
        return id != null ? id.toString() : "unknown";
    }
}
