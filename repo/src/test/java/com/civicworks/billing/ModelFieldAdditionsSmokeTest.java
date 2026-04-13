package com.civicworks.billing;

import com.civicworks.billing.domain.Account;
import com.civicworks.billing.domain.FeeItem;
import com.civicworks.dispatch.domain.DispatchOrder;
import com.civicworks.dispatch.domain.RejectionReason;
import com.civicworks.settlement.domain.Payment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C) Static smoke tests: new C-required fields exist on the domain entities
 * and have JPA column metadata aligned with the V3 migration.
 */
class ModelFieldAdditionsSmokeTest {

    private static jakarta.persistence.Column columnOf(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        return f.getAnnotation(jakarta.persistence.Column.class);
    }

    @Test
    void account_hasAddressFields_andSettersWork() {
        Account a = new Account();
        a.setAddressLine1("1 Main");
        a.setAddressLine2("Apt 2");
        a.setCity("Springfield");
        a.setState("NY");
        a.setPostalCode("12345");
        assertEquals("1 Main", a.getAddressLine1());
        assertEquals("Apt 2", a.getAddressLine2());
        assertEquals("Springfield", a.getCity());
        assertEquals("NY", a.getState());
        assertEquals("12345", a.getPostalCode());
    }

    @Test
    void feeItem_hasTaxableFlag_defaultFalse() {
        FeeItem f = new FeeItem();
        assertFalse(f.isTaxableFlag());
        f.setTaxableFlag(true);
        assertTrue(f.isTaxableFlag());
    }

    @Test
    void dispatchOrder_hasRejectionReasonField_enumBacked() throws Exception {
        DispatchOrder o = new DispatchOrder();
        o.setRejectionReason(RejectionReason.SAFETY_CONCERN);
        assertEquals(RejectionReason.SAFETY_CONCERN, o.getRejectionReason());
        var col = columnOf(DispatchOrder.class, "rejectionReason");
        assertNotNull(col, "rejection_reason column annotation present");
        assertEquals("rejection_reason", col.name());
    }

    @Test
    void payment_hasShiftIdField_andItIsSettable() throws Exception {
        Payment p = new Payment();
        p.setShiftId(42L);
        assertEquals(42L, p.getShiftId());
        var col = columnOf(Payment.class, "shiftId");
        assertNotNull(col);
        assertEquals("shift_id", col.name());
    }
}
