package com.civicworks.settlement.domain;

/**
 * Settlement payment method (api-spec §3.6).
 * Persisted as the enum name in {@code payments.method}.
 */
public enum PaymentMethod {
    CASH,
    CHECK,
    VOUCHER,
    OTHER
}
