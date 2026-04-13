package com.civicworks.platform.security;

import com.civicworks.platform.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Resident-identifier endpoints for {@link UserEntity}. Mirrors the
 * account-side contract: the plaintext resident identifier is sensitive
 * PII, so the response DTOs never include ciphertext, hash, or any other
 * raw protection primitive. Non-privileged authorized callers receive the
 * masked form; privileged readers receive the decrypted value.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserResidentIdController {

    private final UserResidentIdService service;

    public UserResidentIdController(UserResidentIdService service) {
        this.service = service;
    }

    /** Setting a user resident identifier is a SYSTEM_ADMIN-only action. */
    @PostMapping("/{userId}/resident-id")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ResidentIdView> setResidentId(
            @PathVariable Long userId,
            @Valid @RequestBody ResidentIdRequest request) {
        UserEntity saved = service.setResidentId(
                userId, request.residentId(), SecurityUtils.currentUserId());
        boolean privileged = isPrivileged();
        return ResponseEntity.ok(ResidentIdView.from(saved,
                service.viewResidentId(saved, privileged), privileged));
    }

    /**
     * Read the resident id for a user. SYSTEM_ADMIN / AUDITOR see the full
     * plaintext; BILLING_CLERK sees only the masked form.
     */
    @GetMapping("/{userId}/resident-id")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR', 'BILLING_CLERK')")
    public ResponseEntity<ResidentIdView> getResidentId(@PathVariable Long userId) {
        UserEntity user = service.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        boolean privileged = isPrivileged();
        return ResponseEntity.ok(ResidentIdView.from(user,
                service.viewResidentId(user, privileged), privileged));
    }

    /**
     * Deterministic lookup by resident identifier. Plaintext is sent as a
     * query parameter, hashed, and never persisted to logs or audit by
     * this endpoint. Returns a minimal safe projection — never ciphertext
     * or hash.
     */
    @GetMapping("/by-resident-id")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR')")
    public ResponseEntity<UserSummary> findByResidentId(@RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw BusinessException.badRequest(
                    com.civicworks.platform.error.ErrorCode.VALIDATION_ERROR,
                    "q is required");
        }
        UserEntity user = service.findByResidentId(q)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        return ResponseEntity.ok(UserSummary.from(user));
    }

    private static boolean isPrivileged() {
        AuthPrincipal p = SecurityUtils.currentPrincipal();
        return p != null && p.hasAnyRole(Role.SYSTEM_ADMIN, Role.AUDITOR);
    }

    public record ResidentIdRequest(@NotBlank String residentId) {}

    /** Role-aware view: never includes ciphertext or hash. */
    public record ResidentIdView(Long userId, String residentId, boolean masked) {
        public static ResidentIdView from(UserEntity u, String view, boolean privileged) {
            return new ResidentIdView(u.getId(), view, !privileged);
        }
    }

    /** Minimal safe projection for resident-id lookup — no PII echoed. */
    public record UserSummary(Long userId, String username, String status) {
        public static UserSummary from(UserEntity u) {
            return new UserSummary(u.getId(), u.getUsername(), u.getStatus().name());
        }
    }
}
