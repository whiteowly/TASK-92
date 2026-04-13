package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.crypto.CryptoService;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service for resident-identifier handling on {@link UserEntity}.
 *
 * <p>The resident identifier is sensitive PII and is never stored in
 * plaintext. The same two-column strategy used on {@code accounts}
 * (see {@code com.civicworks.billing.application.AccountService}) is
 * applied here to close the field-level encryption contract end-to-end
 * for user workflows:
 * <ul>
 *   <li>{@code users.encrypted_resident_id} — AES-256-GCM ciphertext via
 *       {@link CryptoService#encrypt(String)}.</li>
 *   <li>{@code users.resident_id_hash} — SHA-256 hex via
 *       {@link CryptoService#hash(String)} for deterministic lookup/dedupe
 *       without reintroducing the plaintext.</li>
 * </ul>
 *
 * <p>Audit rows carry the entity id and action only — <b>never the
 * plaintext resident id</b>.
 */
@Service
public class UserResidentIdService {

    private final UserRepository userRepository;
    private final CryptoService crypto;
    private final AuditService auditService;

    public UserResidentIdService(UserRepository userRepository,
                                 CryptoService crypto,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    /**
     * Set the resident identifier on a user atomically. Persists
     * ciphertext + hash; plaintext is never written to a column.
     */
    @Transactional
    public UserEntity setResidentId(Long userId, String residentIdPlaintext, Long actorId) {
        if (residentIdPlaintext == null || residentIdPlaintext.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR,
                    "residentId is required");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        String normalized = residentIdPlaintext.trim();
        user.setEncryptedResidentId(crypto.encrypt(normalized));
        user.setResidentIdHash(crypto.hash(normalized));
        UserEntity saved = userRepository.save(user);
        // Audit carries only the entity id; never the plaintext.
        auditService.log(actorId, "SYSTEM_ADMIN", "USER_RESIDENT_ID_SET",
                "user", userId.toString(), null);
        return saved;
    }

    public Optional<UserEntity> findById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Lookup by plaintext resident identifier via its SHA-256 hash. The
     * plaintext itself is only used transiently to compute the hash and
     * never persisted through this path.
     */
    public Optional<UserEntity> findByResidentId(String residentIdPlaintext) {
        if (residentIdPlaintext == null || residentIdPlaintext.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByResidentIdHash(crypto.hash(residentIdPlaintext.trim()));
    }

    /**
     * Role-safe view of the user resident identifier. Privileged callers
     * receive the decrypted value; all other callers receive the masked
     * form ({@code ****1234}). Ciphertext and hash are never returned.
     */
    public String viewResidentId(UserEntity user, boolean privileged) {
        if (user.getEncryptedResidentId() == null) return null;
        String plain = crypto.decrypt(user.getEncryptedResidentId());
        return privileged ? plain : crypto.mask(plain);
    }
}
