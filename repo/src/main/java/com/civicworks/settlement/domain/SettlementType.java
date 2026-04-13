package com.civicworks.settlement.domain;

/**
 * Settlement strategy applied when posting a payment (api-spec §3.6).
 */
public enum SettlementType {
    FULL,
    SPLIT,
    EVEN_SPLIT
}
