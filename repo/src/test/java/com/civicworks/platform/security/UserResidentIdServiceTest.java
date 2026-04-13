package com.civicworks.platform.security;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Resident-identifier protection on {@link UserEntity}: ciphertext + hash
 * persist, plaintext never does, and audit rows never carry the plaintext.
 * Mirrors the contract enforced by
 * {@code com.civicworks.billing.application.AccountService} on accounts.
 */
@ExtendWith(MockitoExtension.class)
class UserResidentIdServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private AuditService auditService;

    private CryptoService crypto;
    private UserResidentIdService svc;

    @BeforeEach
    void setUp() {
        // Real CryptoService with an ephemeral key so we exercise actual
        // encrypt/hash semantics, not stand-ins.
        crypto = new CryptoService("");
        svc = new UserResidentIdService(userRepo, crypto, auditService);
    }

    @Test
    void userEntity_hasEncryptedAndHashColumns() throws Exception {
        Field enc = UserEntity.class.getDeclaredField("encryptedResidentId");
        Field h = UserEntity.class.getDeclaredField("residentIdHash");
        assertEquals("encrypted_resident_id",
                enc.getAnnotation(jakarta.persistence.Column.class).name());
        assertEquals("resident_id_hash",
                h.getAnnotation(jakarta.persistence.Column.class).name());
    }

    @Test
    void setResidentId_persistsCiphertextAndHash_neverPlaintext() {
        UserEntity u = new UserEntity();
        u.setId(77L);
        when(userRepo.findById(77L)).thenReturn(Optional.of(u));
        when(userRepo.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity saved = svc.setResidentId(77L, "RES-USER-999", 1L);

        verify(userRepo).save(captor.capture());
        UserEntity persisted = captor.getValue();
        assertNotNull(persisted.getEncryptedResidentId());
        assertNotNull(persisted.getResidentIdHash());
        // Ciphertext must not contain the plaintext.
        assertFalse(persisted.getEncryptedResidentId().contains("RES-USER-999"));
        // Hash must equal a fresh hash of the same normalized input
        // (deterministic lookup property).
        assertEquals(crypto.hash("RES-USER-999"), persisted.getResidentIdHash());
        // Decryption recovers the original.
        assertEquals("RES-USER-999", crypto.decrypt(persisted.getEncryptedResidentId()));
        assertSame(persisted, saved);
    }

    @Test
    void setResidentId_trimsWhitespaceBeforeHash() {
        UserEntity u = new UserEntity();
        u.setId(77L);
        when(userRepo.findById(77L)).thenReturn(Optional.of(u));
        when(userRepo.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        svc.setResidentId(77L, "  RES-USER-999\t", 1L);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepo).save(captor.capture());
        assertEquals(crypto.hash("RES-USER-999"), captor.getValue().getResidentIdHash());
    }

    @Test
    void setResidentId_auditEntry_carriesEntityIdOnly_noPlaintext() {
        UserEntity u = new UserEntity();
        u.setId(77L);
        when(userRepo.findById(77L)).thenReturn(Optional.of(u));
        when(userRepo.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        svc.setResidentId(77L, "RES-USER-999", 42L);

        // Audit call: actor=42, role=SYSTEM_ADMIN, action=USER_RESIDENT_ID_SET,
        // entityType=user, entityId=77, details=null (explicitly no plaintext).
        verify(auditService).log(eq(42L), eq("SYSTEM_ADMIN"),
                eq("USER_RESIDENT_ID_SET"), eq("user"),
                eq("77"), isNull());
        // Defensive: assert we never called auditService with anything
        // containing the plaintext.
        verify(auditService, never()).log(any(), any(), any(), any(), any(),
                argThat(details -> details != null && details.contains("RES-USER-999")));
    }

    @Test
    void setResidentId_rejectsBlankOrNullInput() {
        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> svc.setResidentId(77L, null, 1L));
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> svc.setResidentId(77L, "   ", 1L));
        assertEquals("VALIDATION_ERROR", ex1.getCode());
        assertEquals("VALIDATION_ERROR", ex2.getCode());
        verify(userRepo, never()).save(any());
    }

    @Test
    void setResidentId_nonExistentUser_throws404() {
        when(userRepo.findById(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.setResidentId(999L, "RES", 1L));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void findByResidentId_usesHashLookup_deterministic() {
        UserEntity u = new UserEntity();
        u.setId(77L);
        String h = crypto.hash("RES-USER-999");
        when(userRepo.findByResidentIdHash(h)).thenReturn(Optional.of(u));

        Optional<UserEntity> found = svc.findByResidentId("RES-USER-999");
        assertTrue(found.isPresent());
        assertEquals(77L, found.get().getId());

        // Same input hashed twice ⇒ same hash ⇒ same lookup.
        assertEquals(crypto.hash("RES-USER-999"), h);
    }

    @Test
    void findByResidentId_blankReturnsEmpty_doesNotTouchRepo() {
        assertTrue(svc.findByResidentId(null).isEmpty());
        assertTrue(svc.findByResidentId("  ").isEmpty());
        verify(userRepo, never()).findByResidentIdHash(any());
    }

    @Test
    void viewResidentId_masksForNonPrivileged_revealsForPrivileged() {
        UserEntity u = new UserEntity();
        u.setEncryptedResidentId(crypto.encrypt("RES-USER-999"));

        String masked = svc.viewResidentId(u, false);
        assertNotEquals("RES-USER-999", masked);
        assertTrue(masked.startsWith("****"));

        String full = svc.viewResidentId(u, true);
        assertEquals("RES-USER-999", full);
    }

    @Test
    void viewResidentId_whenUnset_returnsNull() {
        UserEntity u = new UserEntity();
        assertNull(svc.viewResidentId(u, true));
        assertNull(svc.viewResidentId(u, false));
    }
}
