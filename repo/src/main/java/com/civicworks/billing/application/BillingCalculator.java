package com.civicworks.billing.application;

import com.civicworks.billing.domain.FeeItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculation strategy for {@link FeeItem.CalculationType}. Centralized
 * so unit tests can pin behaviour without having to bring up the full
 * billing-run wiring.
 */
public final class BillingCalculator {

    private BillingCalculator() {}

    /**
     * Calculation result: the line quantity, the unit rate that produced it,
     * and the cent-rounded amount. The amount field is the value that lands
     * on the bill line item.
     */
    public record LineCalc(BigDecimal quantity, BigDecimal unitRate, BigDecimal amount) {}

    public static LineCalc calculate(FeeItem fee, BigDecimal usageUnits) {
        BigDecimal rate = nonNull(fee.getRate());
        BigDecimal quantity;
        switch (fee.getCalculationType()) {
            case FLAT -> {
                // FLAT: amount = rate, quantity is informational (1 unit).
                quantity = BigDecimal.ONE;
            }
            case PER_UNIT -> {
                // PER_UNIT: amount = rate * units. Default quantity = 1 if
                // no usage record exists so per-unit fees still produce a
                // bill line at the unit rate.
                quantity = (usageUnits == null) ? BigDecimal.ONE : usageUnits;
            }
            case METERED -> {
                // METERED: amount = rate * usage. No usage record -> $0.
                quantity = (usageUnits == null) ? BigDecimal.ZERO : usageUnits;
            }
            default -> throw new IllegalStateException("Unknown calc type: " + fee.getCalculationType());
        }
        BigDecimal amount = (fee.getCalculationType() == FeeItem.CalculationType.FLAT)
                ? rate.setScale(2, RoundingMode.HALF_UP)
                : rate.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        return new LineCalc(quantity, rate, amount);
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
