package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.config.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Session TTL is an admin-editable runtime knob. This test proves that a
 * {@code sessionTtlMinutes} edit landed in {@code system_config} via
 * {@code PUT /api/v1/admin/system-config} is picked up on the next login
 * without a restart — and that out-of-range / non-numeric values fall
 * back to the boot default.
 */
class AuthServiceRuntimeTtlTest {

    private UserRepository userRepo;
    private AuthSessionRepository sessionRepo;
    private PasswordEncoder encoder;
    private AuditService auditService;
    private SystemConfigService systemConfig;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        sessionRepo = mock(AuthSessionRepository.class);
        encoder = new BCryptPasswordEncoder();
        auditService = mock(AuditService.class);
        systemConfig = mock(SystemConfigService.class);
    }

    private UserEntity user(String pw) {
        UserEntity u = new UserEntity();
        u.setId(1L); u.setUsername("admin");
        u.setPasswordHash(encoder.encode(pw));
        u.setStatus(UserEntity.UserStatus.ACTIVE);
        u.setRoles(Set.of(Role.SYSTEM_ADMIN));
        return u;
    }

    private long loginAndReadTtlMinutes(AuthService svc) {
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user("secret")));
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<AuthSession> cap = ArgumentCaptor.forClass(AuthSession.class);

        Instant before = Instant.now();
        svc.login("admin", "secret");
        verify(sessionRepo, atLeastOnce()).save(cap.capture());
        AuthSession session = cap.getAllValues().get(cap.getAllValues().size() - 1);

        // Round to minutes away from both sides so tiny execution delay
        // can't produce off-by-one at the boundary.
        return Duration.between(before, session.getExpiresAt()).toMinutes();
    }

    @Test
    void liveAdminEdit_shortensTtlOnNextLogin() {
        AuthService svc = new AuthService(userRepo, sessionRepo, encoder, auditService,
                systemConfig, 480);

        // Initial boot: no edit in system_config → default 480.
        when(systemConfig.getInt(SystemConfigService.KEY_SESSION_TTL_MIN, 480)).thenReturn(480);
        long ttl1 = loginAndReadTtlMinutes(svc);
        assertTrue(ttl1 >= 478 && ttl1 <= 480, "boot TTL should be ~480, got " + ttl1);

        // Admin edits to 60. Service must pick it up on the NEXT login —
        // no restart.
        when(systemConfig.getInt(SystemConfigService.KEY_SESSION_TTL_MIN, 480)).thenReturn(60);
        reset(sessionRepo);
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(i -> i.getArgument(0));
        long ttl2 = loginAndReadTtlMinutes(svc);
        assertTrue(ttl2 >= 58 && ttl2 <= 60, "post-edit TTL should be ~60, got " + ttl2);
    }

    @Test
    void outOfRangeEdit_fallsBackToDefault() {
        AuthService svc = new AuthService(userRepo, sessionRepo, encoder, auditService,
                systemConfig, 480);

        // 30 and 5000 are outside the AdminController-documented [60..1440] range.
        when(systemConfig.getInt(SystemConfigService.KEY_SESSION_TTL_MIN, 480)).thenReturn(30);
        assertEquals(480, svc.currentSessionTtlMinutes());
        when(systemConfig.getInt(SystemConfigService.KEY_SESSION_TTL_MIN, 480)).thenReturn(5000);
        assertEquals(480, svc.currentSessionTtlMinutes());
    }

    @Test
    void nullSystemConfig_fallsBackToDefault() {
        AuthService svc = new AuthService(userRepo, sessionRepo, encoder, auditService,
                null, 480);
        assertEquals(480, svc.currentSessionTtlMinutes());
    }
}
