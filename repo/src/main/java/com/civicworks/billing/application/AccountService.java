package com.civicworks.billing.application;

import com.civicworks.billing.domain.Account;
import com.civicworks.billing.infra.AccountRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.crypto.CryptoService;
import com.civicworks.platform.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service for {@link Account} resident-identifier handling.
 *
 * <p>Resident identifier is sensitive PII. We never store it in plaintext:
 * <ul>
 *   <li>{@code encrypted_resident_id} — AES-256-GCM ciphertext via
 *       {@link CryptoService#encrypt(String)} for authorized retrieval.</li>
 *   <li>{@code resident_id_hash} — SHA-256 hex via
 *       {@link CryptoService#hash(String)} for deterministic lookup/dedupe
 *       without re-exposing the plaintext.</li>
 * </ul>
 * Mirrors the protection strategy used on {@code users.encrypted_resident_id}.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final CryptoService crypto;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepo, CryptoService crypto,
                          AuditService auditService) {
        this.accountRepo = accountRepo;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    @Transactional
    public Account setResidentId(Long accountId, String residentIdPlaintext, Long actorId) {
        if (residentIdPlaintext == null || residentIdPlaintext.isBlank()) {
            throw BusinessException.badRequest(
                    com.civicworks.platform.error.ErrorCode.VALIDATION_ERROR,
                    "residentId is required");
        }
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> BusinessException.notFound("Account not found"));
        String normalized = residentIdPlaintext.trim();
        account.setEncryptedResidentId(crypto.encrypt(normalized));
        account.setResidentIdHash(crypto.hash(normalized));
        Account saved = accountRepo.save(account);
        // Audit references account id only; never log the plaintext id.
        auditService.log(actorId, "BILLING_CLERK", "ACCOUNT_RESIDENT_ID_SET",
                "account", accountId.toString(), null);
        return saved;
    }

    public Optional<Account> findById(Long accountId) {
        return accountRepo.findById(accountId);
    }

    /** Lookup by hash — never accepts ciphertext. Returns nothing if absent. */
    public Optional<Account> findByResidentId(String residentIdPlaintext) {
        if (residentIdPlaintext == null || residentIdPlaintext.isBlank()) {
            return Optional.empty();
        }
        return accountRepo.findByResidentIdHash(crypto.hash(residentIdPlaintext.trim()));
    }

    /**
     * Role-safe view of the resident id. Returns the masked form (last 4 only)
     * for non-privileged roles; the decrypted value is exposed only when the
     * caller is privileged. The raw ciphertext is never returned to API clients.
     */
    public String viewResidentId(Account account, boolean privileged) {
        if (account.getEncryptedResidentId() == null) return null;
        String plain = crypto.decrypt(account.getEncryptedResidentId());
        return privileged ? plain : crypto.mask(plain);
    }
}
