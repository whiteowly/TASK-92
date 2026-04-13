package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.config.SystemConfigService;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {

    private static final String TOKEN_PREFIX = "cwk_sess_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SystemConfigService systemConfig;
    // Boot-time fallback only: the session TTL is read on every login from
    // system_config so SYSTEM_ADMIN edits take effect without a restart.
    private final int defaultSessionTtlMinutes;

    public AuthService(UserRepository userRepository,
                       AuthSessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       SystemConfigService systemConfig,
                       @Value("${civicworks.session.ttl-minutes:480}") int defaultSessionTtlMinutes) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.systemConfig = systemConfig;
        this.defaultSessionTtlMinutes = defaultSessionTtlMinutes;
    }

    /**
     * Effective session TTL in minutes, read at call-time from
     * {@code system_config.sessionTtlMinutes} with the {@code @Value}
     * default as fallback. Out-of-range or non-numeric values in
     * system_config fall back to the default.
     */
    int currentSessionTtlMinutes() {
        int v = systemConfig != null
                ? systemConfig.getInt(SystemConfigService.KEY_SESSION_TTL_MIN, defaultSessionTtlMinutes)
                : defaultSessionTtlMinutes;
        // Guard the contract documented in AdminController (@Min(60)@Max(1440)).
        if (v < 60 || v > 1440) v = defaultSessionTtlMinutes;
        return v;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (user.getStatus() == UserEntity.UserStatus.DISABLED || user.getStatus() == UserEntity.UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "User account is disabled", HttpStatus.LOCKED);
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        String rawToken = generateToken();
        String tokenHash = sha256(rawToken);

        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setTokenHash(tokenHash);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(currentSessionTtlMinutes(), ChronoUnit.MINUTES));
        session.setLastSeenAt(now);
        sessionRepository.save(session);

        auditService.log(user.getId(), rolesString(user), "LOGIN", "user", user.getId().toString(), null);

        return new LoginResult(
                TOKEN_PREFIX + rawToken,
                "Bearer",
                session.getId(),
                session.getExpiresAt(),
                user.getRoles(),
                user.getStatus().name(),
                user.getId()
        );
    }

    @Transactional
    public void logout(String token) {
        String tokenHash = sha256(stripPrefix(token));
        sessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                session.setRevokedReason("LOGOUT");
                sessionRepository.save(session);
                auditService.log(session.getUserId(), null, "LOGOUT", "session",
                        session.getId().toString(), null);
            }
        });
    }

    @Transactional
    public SessionValidation validateToken(String token) {
        String raw = stripPrefix(token);
        String tokenHash = sha256(raw);
        AuthSession session = sessionRepository.findByTokenHash(tokenHash).orElse(null);
        if (session == null) {
            return SessionValidation.invalid(ErrorCode.SESSION_INVALID);
        }
        if (session.isRevoked()) {
            return SessionValidation.invalid(ErrorCode.SESSION_REVOKED);
        }
        if (session.isExpired()) {
            return SessionValidation.invalid(ErrorCode.SESSION_EXPIRED);
        }

        session.setLastSeenAt(Instant.now());
        sessionRepository.save(session);

        UserEntity user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || user.getStatus() != UserEntity.UserStatus.ACTIVE) {
            session.setRevokedAt(Instant.now());
            session.setRevokedReason("USER_DISABLED");
            sessionRepository.save(session);
            return SessionValidation.invalid(ErrorCode.SESSION_REVOKED);
        }

        return SessionValidation.valid(user, session);
    }

    @Transactional
    public void revokeSession(Long sessionId, Long actorId) {
        AuthSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.notFound("Session not found"));
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            session.setRevokedReason("ADMIN_REVOKE");
            sessionRepository.save(session);
            auditService.log(actorId, "SYSTEM_ADMIN", "SESSION_REVOKE", "session",
                    sessionId.toString(), null);
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason, Long actorId) {
        sessionRepository.revokeAllForUser(userId, Instant.now(), reason);
        auditService.log(actorId, null, "SESSION_REVOKE_ALL", "user", userId.toString(), null);
    }

    public List<AuthSession> listSessions(Long userIdFilter) {
        return sessionRepository.findAllFiltered(userIdFilter);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String stripPrefix(String token) {
        if (token != null && token.startsWith(TOKEN_PREFIX)) {
            return token.substring(TOKEN_PREFIX.length());
        }
        return token;
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String rolesString(UserEntity user) {
        return String.join(",", user.getRoles().stream().map(Role::name).toList());
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            Long sessionId,
            Instant expiresAt,
            java.util.Set<Role> roles,
            String userStatus,
            Long userId
    ) {}

    public record SessionValidation(
            boolean valid,
            String errorCode,
            UserEntity user,
            AuthSession session
    ) {
        static SessionValidation valid(UserEntity user, AuthSession session) {
            return new SessionValidation(true, null, user, session);
        }
        static SessionValidation invalid(String errorCode) {
            return new SessionValidation(false, errorCode, null, null);
        }
    }
}
