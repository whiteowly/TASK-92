package com.civicworks.settlement;

import com.civicworks.settlement.domain.Payment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payment.received_at mapping: column metadata exists, default-on-insert wires
 * to created_at, and an explicitly-set value is preserved.
 */
class PaymentReceivedAtTest {

    @Test
    void receivedAt_columnAnnotationPresent() throws Exception {
        Field f = Payment.class.getDeclaredField("receivedAt");
        jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
        assertNotNull(col, "received_at column annotation present");
        assertEquals("received_at", col.name());
    }

    @Test
    void prePersist_defaultsReceivedAtToCreatedAt() throws Exception {
        Payment p = new Payment();
        invokePrePersist(p);
        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getReceivedAt());
        assertEquals(p.getCreatedAt(), p.getReceivedAt(),
                "received_at should default to created_at when not set");
    }

    @Test
    void prePersist_preservesExplicitReceivedAt() throws Exception {
        Payment p = new Payment();
        Instant explicit = Instant.parse("2026-04-13T10:00:00Z");
        p.setReceivedAt(explicit);
        invokePrePersist(p);
        assertEquals(explicit, p.getReceivedAt(),
                "explicit received_at must not be overwritten");
        assertNotEquals(explicit, p.getCreatedAt(),
                "created_at is independent of received_at");
    }

    private static void invokePrePersist(Payment p) throws Exception {
        Method m = Payment.class.getDeclaredMethod("pre");
        m.setAccessible(true);
        m.invoke(p);
    }
}
