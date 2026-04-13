package com.civicworks.platform.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public static BusinessException unprocessable(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.ACCESS_DENIED, message, HttpStatus.FORBIDDEN);
    }
}
