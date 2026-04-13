package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private AuthSessionRepository sessionRepo;
    @Mock private AuditService auditService;

    private PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Pass a null SystemConfigService so the service falls back to the
        // @Value default (480) — mirrors the behavior when system_config has
        // no override row. Integration of runtime reads is covered in
        // AuthServiceRuntimeTtlTest.
        authService = new AuthService(userRepo, sessionRepo, encoder, auditService, null, 480);
    }

    private UserEntity activeUser(String username, String password) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setStatus(UserEntity.UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.SYSTEM_ADMIN));
        return user;
    }

    @Test
    void loginSucceeds() {
        UserEntity user = activeUser("admin", "password123");
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(sessionRepo.save(any())).thenAnswer(i -> {
            AuthSession s = i.getArgument(0);
            s.setId(1L);
            return s;
        });

        AuthService.LoginResult result = authService.login("admin", "password123");
        assertNotNull(result.accessToken());
        assertTrue(result.accessToken().startsWith("cwk_sess_"));
        assertEquals("Bearer", result.tokenType());
        assertTrue(result.roles().contains(Role.SYSTEM_ADMIN));
    }

    @Test
    void loginFailsWithBadPassword() {
        UserEntity user = activeUser("admin", "password123");
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> authService.login("admin", "wrong"));
    }

    @Test
    void loginFailsForDisabledUser() {
        UserEntity user = activeUser("admin", "password123");
        user.setStatus(UserEntity.UserStatus.DISABLED);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login("admin", "password123"));
        assertEquals("USER_DISABLED", ex.getCode());
    }

    @Test
    void loginFailsForUnknownUser() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login("ghost", "any"));
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void tokenHashIsSha256() {
        String hash = AuthService.sha256("test");
        assertEquals(64, hash.length());
        // SHA-256 of "test"
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }

    @Test
    void validateTokenRejectsExpired() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setTokenHash(AuthService.sha256("test"));
        session.setIssuedAt(Instant.now().minus(9, ChronoUnit.HOURS));
        session.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(sessionRepo.findByTokenHash(any())).thenReturn(Optional.of(session));

        AuthService.SessionValidation result = authService.validateToken("cwk_sess_test");
        assertFalse(result.valid());
        assertEquals("SESSION_EXPIRED", result.errorCode());
    }

    @Test
    void validateTokenRejectsRevoked() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setTokenHash(AuthService.sha256("test"));
        session.setIssuedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(8, ChronoUnit.HOURS));
        session.setRevokedAt(Instant.now());

        when(sessionRepo.findByTokenHash(any())).thenReturn(Optional.of(session));

        AuthService.SessionValidation result = authService.validateToken("cwk_sess_test");
        assertFalse(result.valid());
        assertEquals("SESSION_REVOKED", result.errorCode());
    }
}
