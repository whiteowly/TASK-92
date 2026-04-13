package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "billing_runs")
public class BillingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_date", nullable = false)
    private LocalDate cycleDate;

    @Column(name = "cycle_type", nullable = false, length = 20)
    private String cycleType;

    @Column(nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "policy_due_in_days", nullable = false)
    private int policyDueInDays;

    @Column(name = "policy_effective_from", nullable = false)
    private Instant policyEffectiveFrom;

    @Column(name = "bills_generated", nullable = false)
    private int billsGenerated = 0;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); startedAt = createdAt; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getCycleDate() { return cycleDate; }
    public void setCycleDate(LocalDate cycleDate) { this.cycleDate = cycleDate; }
    public String getCycleType() { return cycleType; }
    public void setCycleType(String cycleType) { this.cycleType = cycleType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPolicyDueInDays() { return policyDueInDays; }
    public void setPolicyDueInDays(int policyDueInDays) { this.policyDueInDays = policyDueInDays; }
    public Instant getPolicyEffectiveFrom() { return policyEffectiveFrom; }
    public void setPolicyEffectiveFrom(Instant policyEffectiveFrom) { this.policyEffectiveFrom = policyEffectiveFrom; }
    public int getBillsGenerated() { return billsGenerated; }
    public void setBillsGenerated(int billsGenerated) { this.billsGenerated = billsGenerated; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
