package com.civicworks.platform.security;

import com.civicworks.platform.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Token authentication filter behavior: absent/invalid tokens MUST leave the
 * security context empty (causing downstream to respond with the 401
 * envelope). Only valid sessions produce an Authentication with the user's
 * roles materialized as ROLE_* authorities.
 */
class TokenAuthenticationFilterTest {

    private AuthService authService;
    private TokenAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        filter = new TokenAuthenticationFilter(authService);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void missingAuthorizationHeader_leavesContextUnauthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, resp);
        verify(authService, never()).validateToken(any());
    }

    @Test
    void nonBearerHeader_isIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authService, never()).validateToken(any());
    }

    @Test
    void invalidToken_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer cwk_sess_bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(authService.validateToken("cwk_sess_bad"))
                .thenReturn(AuthService.SessionValidation.invalid(ErrorCode.SESSION_INVALID));

        filter.doFilter(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validToken_setsAuthenticationWithRoleAuthorities() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer cwk_sess_ok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin");
        user.setRoles(Set.of(Role.SYSTEM_ADMIN, Role.BILLING_CLERK));
        AuthSession session = new AuthSession();
        when(authService.validateToken("cwk_sess_ok"))
                .thenReturn(AuthService.SessionValidation.valid(user, session));

        filter.doFilter(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_BILLING_CLERK")));
        assertTrue(auth.getPrincipal() instanceof AuthPrincipal);
    }
}
