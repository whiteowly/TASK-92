package com.civicworks.billing;

import com.civicworks.billing.application.BillingCalculator;
import com.civicworks.billing.application.BillingCalculator.LineCalc;
import com.civicworks.billing.domain.FeeItem;
import com.civicworks.billing.domain.FeeItem.CalculationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D) Billing calculation strategies for FLAT, PER_UNIT, METERED.
 */
class BillingCalculatorTest {

    private static FeeItem fee(CalculationType type, String rate) {
        FeeItem f = new FeeItem();
        f.setCalculationType(type);
        f.setRate(new BigDecimal(rate));
        f.setName("test");
        return f;
    }

    @Test
    void flat_amountIsRate_quantityIsOne_usageIgnored() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.FLAT, "12.34"),
                new BigDecimal("99"));
        assertEquals(0, new BigDecimal("12.34").compareTo(r.amount()));
        assertEquals(0, BigDecimal.ONE.compareTo(r.quantity()));
        assertEquals(0, new BigDecimal("12.34").compareTo(r.unitRate()));
    }

    @Test
    void perUnit_multiplyRateByUnits() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.PER_UNIT, "1.50"),
                new BigDecimal("4"));
        assertEquals(0, new BigDecimal("6.00").compareTo(r.amount()));
        assertEquals(0, new BigDecimal("4").compareTo(r.quantity()));
    }

    @Test
    void perUnit_decimalQuantity_rounded() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.PER_UNIT, "0.075"),
                new BigDecimal("3.333"));
        // 0.075 * 3.333 = 0.2499 75 -> half-up to 0.25
        assertEquals(0, new BigDecimal("0.25").compareTo(r.amount()),
                "expected 0.25 got " + r.amount());
    }

    @Test
    void perUnit_noUsage_defaultsToOneUnit() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.PER_UNIT, "9.99"), null);
        assertEquals(0, new BigDecimal("9.99").compareTo(r.amount()));
        assertEquals(0, BigDecimal.ONE.compareTo(r.quantity()));
    }

    @Test
    void metered_multiplyRateByUsage() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.METERED, "0.10"),
                new BigDecimal("125"));
        assertEquals(0, new BigDecimal("12.50").compareTo(r.amount()));
        assertEquals(0, new BigDecimal("125").compareTo(r.quantity()));
    }

    @Test
    void metered_zeroUsage_amountIsZero() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.METERED, "5.00"),
                BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.quantity()));
    }

    @Test
    void metered_noUsageRecord_amountIsZero() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.METERED, "5.00"), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.quantity()));
    }

    @Test
    void metered_decimalUsage_centRounded() {
        LineCalc r = BillingCalculator.calculate(fee(CalculationType.METERED, "0.123"),
                new BigDecimal("100.5"));
        // 0.123 * 100.5 = 12.3615 -> 12.36
        assertEquals(0, new BigDecimal("12.36").compareTo(r.amount()));
    }
}
