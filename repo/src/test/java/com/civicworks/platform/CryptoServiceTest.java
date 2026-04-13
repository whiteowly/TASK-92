package com.civicworks.platform;

import com.civicworks.platform.crypto.CryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService("dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlcw==");

    @Test
    void encryptDecryptRoundTrip() {
        String plaintext = "SSN-123-45-6789";
        String encrypted = crypto.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        String decrypted = crypto.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void nullReturnsNull() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void hashIsDeterministic() {
        String hash1 = crypto.hash("test");
        String hash2 = crypto.hash("test");
        assertEquals(hash1, hash2);
    }

    @Test
    void maskShowsLast4() {
        assertEquals("****6789", crypto.mask("123456789"));
    }

    @Test
    void maskShortValue() {
        assertEquals("****", crypto.mask("ab"));
    }

    @Test
    void encryptedOutputIsDifferentEachTime() {
        String e1 = crypto.encrypt("same");
        String e2 = crypto.encrypt("same");
        assertNotEquals(e1, e2, "Different IV should produce different ciphertext");
    }
}
