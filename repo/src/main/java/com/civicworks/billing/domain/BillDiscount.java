package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bill_discounts")
public class BillDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType; // PERCENTAGE, FIXED

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public void setAppliedAmount(BigDecimal appliedAmount) { this.appliedAmount = appliedAmount; }
    public Long getAppliedBy() { return appliedBy; }
    public void setAppliedBy(Long appliedBy) { this.appliedBy = appliedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
