package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return ResponseEntity.ok(new LoginResponse(
                result.accessToken(), result.tokenType(), result.sessionId(),
                result.expiresAt(), result.roles(), result.userStatus()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<SessionResponse>> listSessions(
            @RequestParam(required = false) Long userId) {
        List<AuthSession> sessions = authService.listSessions(userId);
        List<SessionResponse> response = sessions.stream()
                .map(s -> new SessionResponse(s.getId(), s.getUserId(), s.getIssuedAt(),
                        s.getExpiresAt(), s.getRevokedAt(), s.getRevokedReason(), s.getLastSeenAt()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> revokeSession(@PathVariable Long sessionId) {
        AuthPrincipal principal = SecurityUtils.currentPrincipal();
        authService.revokeSession(sessionId, principal.userId());
        return ResponseEntity.ok().build();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, Long sessionId,
                                Instant expiresAt, Set<Role> roles, String userStatus) {}
    public record SessionResponse(Long sessionId, Long userId, Instant issuedAt, Instant expiresAt,
                                  Instant revokedAt, String revokedReason, Instant lastSeenAt) {}
}
