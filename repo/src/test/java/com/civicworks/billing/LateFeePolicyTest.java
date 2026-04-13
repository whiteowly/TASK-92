package com.civicworks.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class LateFeePolicyTest {

    private static final BigDecimal RATE = new BigDecimal("0.05");
    private static final BigDecimal CAP = new BigDecimal("50.00");

    BigDecimal computeLateFee(BigDecimal base, BigDecimal existingFees) {
        BigDecimal fee = base.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = CAP.subtract(existingFees);
        return fee.min(remaining).max(BigDecimal.ZERO);
    }

    @Test
    void basicLateFee() {
        BigDecimal fee = computeLateFee(new BigDecimal("200.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("10.00"), fee);
    }

    @Test
    void lateFeeRespectsCap() {
        BigDecimal fee = computeLateFee(new BigDecimal("2000.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("50.00"), fee, "Should cap at $50");
    }

    @Test
    void lateFeeCapPartiallyUsed() {
        BigDecimal fee = computeLateFee(new BigDecimal("200.00"), new BigDecimal("45.00"));
        assertEquals(new BigDecimal("5.00"), fee, "Remaining cap is $5");
    }

    @Test
    void lateFeeCapExhausted() {
        BigDecimal fee = computeLateFee(new BigDecimal("200.00"), new BigDecimal("50.00"));
        assertEquals(0, fee.compareTo(BigDecimal.ZERO), "No more fees when cap exhausted");
    }

    @Test
    void lateFeeRoundingHalfUp() {
        BigDecimal fee = computeLateFee(new BigDecimal("33.33"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("1.67"), fee);
    }

    @Test
    void fivePercentOfSmallAmount() {
        BigDecimal fee = computeLateFee(new BigDecimal("10.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.50"), fee);
    }
}
