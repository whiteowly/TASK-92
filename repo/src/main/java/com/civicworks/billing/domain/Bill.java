package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "billing_run_id")
    private Long billingRunId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "cycle_date", nullable = false)
    private LocalDate cycleDate;

    @Column(name = "cycle_type", nullable = false, length = 20)
    private String cycleType;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "policy_due_in_days", nullable = false)
    private int policyDueInDays;

    @Column(name = "original_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "late_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BillStatus status = BillStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public enum BillStatus { OPEN, PAID, OVERDUE, CLOSED }

    public void recalculateBalance() {
        this.balance = originalAmount
                .subtract(discountAmount)
                .add(lateFeeAmount)
                .subtract(paidAmount);
        if (this.balance.compareTo(BigDecimal.ZERO) <= 0) {
            this.balance = BigDecimal.ZERO;
            this.status = BillStatus.PAID;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBillingRunId() { return billingRunId; }
    public void setBillingRunId(Long billingRunId) { this.billingRunId = billingRunId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public LocalDate getCycleDate() { return cycleDate; }
    public void setCycleDate(LocalDate cycleDate) { this.cycleDate = cycleDate; }
    public String getCycleType() { return cycleType; }
    public void setCycleType(String cycleType) { this.cycleType = cycleType; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getPolicyDueInDays() { return policyDueInDays; }
    public void setPolicyDueInDays(int policyDueInDays) { this.policyDueInDays = policyDueInDays; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public BillStatus getStatus() { return status; }
    public void setStatus(BillStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
