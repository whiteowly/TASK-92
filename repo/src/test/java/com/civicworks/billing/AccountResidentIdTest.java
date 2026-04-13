package com.civicworks.billing;

import com.civicworks.billing.application.AccountService;
import com.civicworks.billing.domain.Account;
import com.civicworks.billing.infra.AccountRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.crypto.CryptoService;
import com.civicworks.platform.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resident identifier on Account: persistence + protection.
 * The plaintext is never written to the row; we keep the ciphertext +
 * SHA-256 hash via {@link CryptoService}, mirroring the users-table strategy.
 */
@ExtendWith(MockitoExtension.class)
class AccountResidentIdTest {

    @Mock private AccountRepository accountRepo;
    @Mock private AuditService auditService;

    private CryptoService crypto;
    private AccountService svc;

    @BeforeEach
    void setUp() {
        // Use a real CryptoService with an ephemeral random key so we exercise
        // actual encrypt/hash semantics, not stub stand-ins.
        crypto = new CryptoService("");
        svc = new AccountService(accountRepo, crypto, auditService);
    }

    @Test
    void account_hasEncryptedAndHashColumns() throws Exception {
        Field enc = Account.class.getDeclaredField("encryptedResidentId");
        Field h = Account.class.getDeclaredField("residentIdHash");
        assertEquals("encrypted_resident_id",
                enc.getAnnotation(jakarta.persistence.Column.class).name());
        assertEquals("resident_id_hash",
                h.getAnnotation(jakarta.persistence.Column.class).name());
    }

    @Test
    void setResidentId_persistsCiphertextAndHash_neverPlaintext() {
        Account a = new Account();
        a.setId(42L);
        when(accountRepo.findById(42L)).thenReturn(Optional.of(a));
        when(accountRepo.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        Account saved = svc.setResidentId(42L, "RES-12345", 1L);

        verify(accountRepo).save(captor.capture());
        Account persisted = captor.getValue();
        assertNotNull(persisted.getEncryptedResidentId());
        assertNotNull(persisted.getResidentIdHash());
        // Persisted ciphertext must NOT contain the plaintext.
        assertFalse(persisted.getEncryptedResidentId().contains("RES-12345"));
        // Hash must equal a fresh hash of the same input (deterministic lookup).
        assertEquals(crypto.hash("RES-12345"), persisted.getResidentIdHash());
        // Decryption recovers the original.
        assertEquals("RES-12345", crypto.decrypt(persisted.getEncryptedResidentId()));
        assertSame(persisted, saved);

        verify(auditService).log(eq(1L), eq("BILLING_CLERK"),
                eq("ACCOUNT_RESIDENT_ID_SET"), eq("account"), eq("42"), any());
    }

    @Test
    void setResidentId_rejectsBlankInput() {
        assertThrows(BusinessException.class,
                () -> svc.setResidentId(42L, "  ", 1L));
        assertThrows(BusinessException.class,
                () -> svc.setResidentId(42L, null, 1L));
    }

    @Test
    void findByResidentId_usesHashLookup() {
        Account a = new Account();
        a.setId(42L);
        a.setResidentIdHash(crypto.hash("RES-12345"));
        when(accountRepo.findByResidentIdHash(crypto.hash("RES-12345")))
                .thenReturn(Optional.of(a));

        Optional<Account> found = svc.findByResidentId("RES-12345");
        assertTrue(found.isPresent());
        assertEquals(42L, found.get().getId());
    }

    @Test
    void viewResidentId_masksForNonPrivileged_andRevealsForPrivileged() {
        Account a = new Account();
        a.setEncryptedResidentId(crypto.encrypt("RES-12345"));

        String masked = svc.viewResidentId(a, false);
        assertNotEquals("RES-12345", masked, "non-privileged view must not leak plaintext");
        assertTrue(masked.startsWith("****"), "expected masked form, got: " + masked);

        String full = svc.viewResidentId(a, true);
        assertEquals("RES-12345", full);
    }
}
