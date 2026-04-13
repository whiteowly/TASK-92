package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Per-account, per-fee usage units captured prior to a billing cycle.
 * Used by PER_UNIT and METERED calculation strategies.
 */
@Entity
@Table(name = "billing_usage")
public class BillingUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false) private Long accountId;
    @Column(name = "fee_item_id", nullable = false) private Long feeItemId;
    @Column(name = "cycle_date", nullable = false) private LocalDate cycleDate;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal units = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getFeeItemId() { return feeItemId; }
    public void setFeeItemId(Long feeItemId) { this.feeItemId = feeItemId; }
    public LocalDate getCycleDate() { return cycleDate; }
    public void setCycleDate(LocalDate cycleDate) { this.cycleDate = cycleDate; }
    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }
    public Instant getCreatedAt() { return createdAt; }
}
