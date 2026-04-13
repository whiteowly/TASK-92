package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bill_late_fees")
public class BillLateFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "eligible_date", nullable = false)
    private LocalDate eligibleDate;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @PrePersist
    void prePersist() { appliedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public LocalDate getEligibleDate() { return eligibleDate; }
    public void setEligibleDate(LocalDate eligibleDate) { this.eligibleDate = eligibleDate; }
    public Instant getAppliedAt() { return appliedAt; }
}
