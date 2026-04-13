package com.civicworks.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code,
    String message,
    List<FieldError> details,
    String requestId
) {
    public record FieldError(String field, String issue) {}

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(code, message, null, requestId);
    }

    public static ApiError withDetails(String code, String message, List<FieldError> details, String requestId) {
        return new ApiError(code, message, details, requestId);
    }
}
