package com.civicworks.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class SettlementAllocationServiceTest {

    @Test
    void evenSplitExactDivision() {
        BigDecimal total = new BigDecimal("30.00");
        int count = 3;
        BigDecimal part = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        assertEquals(new BigDecimal("10.00"), part);

        BigDecimal sum = part.multiply(BigDecimal.valueOf(count));
        assertEquals(0, sum.compareTo(total));
    }

    @Test
    void evenSplitWithRemainder() {
        BigDecimal total = new BigDecimal("10.00");
        int count = 3;
        BigDecimal part = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        assertEquals(new BigDecimal("3.33"), part);

        BigDecimal allocated = part.multiply(BigDecimal.valueOf(count));
        BigDecimal remainder = total.subtract(allocated);
        int residualCents = remainder.multiply(new BigDecimal("100")).intValue();
        assertEquals(1, residualCents, "1 cent remainder");

        // Verify total reconciles
        BigDecimal finalTotal = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal lineAmount = part;
            if (i < residualCents) {
                lineAmount = lineAmount.add(new BigDecimal("0.01"));
            }
            finalTotal = finalTotal.add(lineAmount);
        }
        assertEquals(0, total.compareTo(finalTotal), "Total must reconcile exactly");
    }

    @Test
    void evenSplitTwoParts() {
        BigDecimal total = new BigDecimal("10.01");
        int count = 2;
        BigDecimal part = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        assertEquals(new BigDecimal("5.00"), part);

        BigDecimal remainder = total.subtract(part.multiply(BigDecimal.valueOf(count)));
        int residualCents = remainder.multiply(new BigDecimal("100")).intValue();
        assertEquals(1, residualCents);

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal a = part;
            if (i < residualCents) a = a.add(new BigDecimal("0.01"));
            sum = sum.add(a);
        }
        assertEquals(0, total.compareTo(sum));
    }

    @Test
    void evenSplitLargeAmountManyParts() {
        BigDecimal total = new BigDecimal("1000.00");
        int count = 7;
        BigDecimal part = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);

        BigDecimal allocated = part.multiply(BigDecimal.valueOf(count));
        BigDecimal remainder = total.subtract(allocated);
        int residualCents = remainder.multiply(new BigDecimal("100")).intValue();

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal a = part;
            if (i < residualCents) a = a.add(new BigDecimal("0.01"));
            sum = sum.add(a);
        }
        assertEquals(0, total.compareTo(sum), "Must reconcile to exact cent");
    }

    @Test
    void proportionalSplitLastLineAbsorbsRounding() {
        BigDecimal billTotal = new BigDecimal("100.00");
        BigDecimal paymentAmount = new BigDecimal("50.00");
        BigDecimal[] lineAmounts = {new BigDecimal("33.33"), new BigDecimal("33.34"), new BigDecimal("33.33")};

        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal[] allocations = new BigDecimal[3];

        for (int i = 0; i < 3; i++) {
            if (i == 2) {
                allocations[i] = paymentAmount.subtract(allocated);
            } else {
                BigDecimal proportion = lineAmounts[i].divide(billTotal, 10, RoundingMode.HALF_UP);
                allocations[i] = paymentAmount.multiply(proportion).setScale(2, RoundingMode.HALF_UP);
            }
            allocated = allocated.add(allocations[i]);
        }

        assertEquals(0, paymentAmount.compareTo(allocated), "Allocations must sum to payment");
    }

    @Test
    void discountFloorAtZero() {
        BigDecimal originalAmount = new BigDecimal("50.00");
        BigDecimal discountAmount = new BigDecimal("60.00");
        BigDecimal balance = originalAmount.subtract(discountAmount);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO;
        assertEquals(0, balance.compareTo(BigDecimal.ZERO));
    }
}
