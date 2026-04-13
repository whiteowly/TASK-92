package com.civicworks.platform.security;

import com.civicworks.platform.error.ApiError;
import com.civicworks.platform.error.ApiErrorResponse;
import com.civicworks.platform.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class AuthEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String requestId = request.getAttribute("requestId") != null
                ? request.getAttribute("requestId").toString() : "unknown";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                ApiError.of(ErrorCode.SESSION_INVALID, "Authentication required", requestId));
        MAPPER.writeValue(response.getOutputStream(), body);
    }
}
