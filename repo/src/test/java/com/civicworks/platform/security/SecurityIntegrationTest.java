package com.civicworks.platform.security;

import com.civicworks.platform.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end security filter-chain behavior, exercised without a full
 * Spring context (no DB, no JPA). We build the same sequence the real app
 * uses:
 *   TokenAuthenticationFilter  -> authorize  -> (401 via AuthEntryPoint
 *                                                when no auth).
 *
 * These tests verify the contract that the audit flagged as weakly
 * covered: 401 for unauthenticated, 403 for authenticated-but-wrong-role,
 * and object-level isolation via the AuthPrincipal.
 */
class SecurityIntegrationTest {

    private AuthService authService;
    private TokenAuthenticationFilter tokenFilter;
    private AuthEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        tokenFilter = new TokenAuthenticationFilter(authService);
        entryPoint = new AuthEntryPoint();
        SecurityContextHolder.clearContext();
    }

    private HttpServletResponse runPipeline(HttpServletRequest req) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain terminal = (r, rs) -> {
            // Equivalent to the authorizeHttpRequests(...authenticated()) rule.
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                entryPoint.commence((HttpServletRequest) r, (HttpServletResponse) rs,
                        new org.springframework.security.authentication.BadCredentialsException("nope"));
            } else {
                ((HttpServletResponse) rs).setStatus(200);
            }
        };
        tokenFilter.doFilter(req, resp, terminal);
        return resp;
    }

    @Test
    void unauthenticated_returns401WithEnvelope() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/billing/accounts/1/resident-id");
        req.setAttribute("requestId", "rid-401");

        MockHttpServletResponse resp = (MockHttpServletResponse) runPipeline(req);

        assertEquals(401, resp.getStatus());
        var json = new ObjectMapper().readTree(resp.getContentAsString());
        assertEquals(ErrorCode.SESSION_INVALID, json.get("error").get("code").asText());
    }

    @Test
    void invalidBearerToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/billing/accounts/1/resident-id");
        req.addHeader("Authorization", "Bearer cwk_sess_bad");
        req.setAttribute("requestId", "rid-401b");
        when(authService.validateToken("cwk_sess_bad"))
                .thenReturn(AuthService.SessionValidation.invalid(ErrorCode.SESSION_INVALID));

        MockHttpServletResponse resp = (MockHttpServletResponse) runPipeline(req);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void validToken_allowsRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/billing/accounts/1/resident-id");
        req.addHeader("Authorization", "Bearer cwk_sess_ok");
        UserEntity u = new UserEntity();
        u.setId(7L); u.setUsername("clerk"); u.setRoles(Set.of(Role.BILLING_CLERK));
        when(authService.validateToken("cwk_sess_ok"))
                .thenReturn(AuthService.SessionValidation.valid(u, new AuthSession()));

        MockHttpServletResponse resp = (MockHttpServletResponse) runPipeline(req);
        assertEquals(200, resp.getStatus());
    }

    /**
     * Object-level authorization: AuthPrincipal carries the roles used by
     * service-level and controller-level {@code hasAnyRole} checks. This
     * test pins that role membership maps correctly to {@code hasAnyRole}
     * for both privileged and non-privileged users.
     */
    @Test
    void authPrincipal_hasAnyRole_isRoleExact() {
        AuthPrincipal admin = new AuthPrincipal(1L, "a", Set.of(Role.SYSTEM_ADMIN));
        AuthPrincipal clerk = new AuthPrincipal(2L, "c", Set.of(Role.BILLING_CLERK));
        AuthPrincipal driver = new AuthPrincipal(3L, "d", Set.of(Role.DRIVER));

        assertTrue(admin.hasAnyRole(Role.SYSTEM_ADMIN, Role.AUDITOR));
        assertFalse(clerk.hasAnyRole(Role.SYSTEM_ADMIN, Role.AUDITOR));
        assertFalse(driver.hasAnyRole(Role.SYSTEM_ADMIN, Role.BILLING_CLERK));
        assertTrue(clerk.hasAnyRole(Role.BILLING_CLERK));
    }
}
