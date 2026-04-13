package com.civicworks.billing.api;

import com.civicworks.billing.application.AccountService;
import com.civicworks.billing.domain.Account;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.security.AuthPrincipal;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Resident-identifier endpoints for {@link Account}.
 *
 * <p>The plaintext resident id is sensitive PII. This controller never
 * returns the encrypted ciphertext or raw hash — only either the decrypted
 * value (privileged callers) or a masked form (other authorized callers).
 */
@RestController
@RequestMapping("/api/v1/billing/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/resident-id")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK')")
    public ResponseEntity<ResidentIdView> setResidentId(
            @PathVariable Long accountId,
            @Valid @RequestBody ResidentIdRequest request) {
        Account saved = accountService.setResidentId(
                accountId, request.residentId(), SecurityUtils.currentUserId());
        // Echo back the role-safe view, never the ciphertext.
        boolean privileged = isPrivileged();
        return ResponseEntity.status(HttpStatus.OK).body(
                ResidentIdView.from(saved, accountService.viewResidentId(saved, privileged), privileged));
    }

    @GetMapping("/{accountId}/resident-id")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK', 'AUDITOR')")
    public ResponseEntity<ResidentIdView> getResidentId(@PathVariable Long accountId) {
        Account account = accountService.findById(accountId)
                .orElseThrow(() -> BusinessException.notFound("Account not found"));
        boolean privileged = isPrivileged();
        return ResponseEntity.ok(ResidentIdView.from(
                account, accountService.viewResidentId(account, privileged), privileged));
    }

    /**
     * Lookup by resident identifier using the deterministic hash. Plaintext
     * is sent as a query parameter and never persisted to logs/audit by this
     * endpoint. Returns an empty 404 envelope when no account matches.
     */
    @GetMapping("/by-resident-id")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK')")
    public ResponseEntity<AccountSummary> findByResidentId(@RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw BusinessException.badRequest(
                    com.civicworks.platform.error.ErrorCode.VALIDATION_ERROR,
                    "q is required");
        }
        Account account = accountService.findByResidentId(q)
                .orElseThrow(() -> BusinessException.notFound("Account not found"));
        return ResponseEntity.ok(AccountSummary.from(account));
    }

    /** Privileged readers (full plaintext): SYSTEM_ADMIN and AUDITOR. */
    private static boolean isPrivileged() {
        AuthPrincipal p = SecurityUtils.currentPrincipal();
        return p != null && p.hasAnyRole(Role.SYSTEM_ADMIN, Role.AUDITOR);
    }

    public record ResidentIdRequest(@NotBlank String residentId) {}

    /**
     * Safe response shape — exposes account id and the role-appropriate
     * resident-id view, plus a {@code masked} flag so clients know whether
     * the value has been redacted. Never includes ciphertext or raw hash.
     */
    public record ResidentIdView(Long accountId, String residentId, boolean masked) {
        public static ResidentIdView from(Account account, String view, boolean privileged) {
            return new ResidentIdView(account.getId(), view, !privileged);
        }
    }

    /** Minimal account projection for resident-id lookup — no PII echoed. */
    public record AccountSummary(Long accountId, Long userId, String name, String status) {
        public static AccountSummary from(Account a) {
            return new AccountSummary(a.getId(), a.getUserId(), a.getName(), a.getStatus());
        }
    }
}
