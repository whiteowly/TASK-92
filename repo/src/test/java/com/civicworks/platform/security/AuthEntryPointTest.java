package com.civicworks.platform.security;

import com.civicworks.platform.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unauthenticated callers hitting a protected endpoint must receive the
 * project's standard 401 error envelope — not a server-default HTML page
 * and not a 200. This test pins that contract at the entry-point level.
 */
class AuthEntryPointTest {

    @Test
    void writes401WithStandardEnvelope() throws Exception {
        AuthEntryPoint entry = new AuthEntryPoint();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("requestId", "req-123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        entry.commence(req, resp, new AuthenticationException("x") {});

        assertEquals(401, resp.getStatus());
        assertEquals("application/json", resp.getContentType());
        JsonNode body = new ObjectMapper().readTree(resp.getContentAsString());
        assertEquals(ErrorCode.SESSION_INVALID, body.get("error").get("code").asText());
        assertEquals("req-123", body.get("error").get("requestId").asText());
    }

    @Test
    void requestIdFallsBackToUnknownWhenMissing() throws Exception {
        AuthEntryPoint entry = new AuthEntryPoint();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        entry.commence(req, resp, new AuthenticationException("x") {});

        JsonNode body = new ObjectMapper().readTree(resp.getContentAsString());
        assertEquals("unknown", body.get("error").get("requestId").asText());
    }
}
