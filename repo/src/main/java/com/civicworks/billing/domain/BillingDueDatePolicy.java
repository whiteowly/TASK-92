package com.civicworks.billing.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "billing_due_date_policy")
public class BillingDueDatePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_type", nullable = false, length = 20)
    private String cycleType;

    @Column(name = "due_in_days", nullable = false)
    private int dueInDays;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getCycleType() { return cycleType; }
    public void setCycleType(String cycleType) { this.cycleType = cycleType; }
    public int getDueInDays() { return dueInDays; }
    public void setDueInDays(int dueInDays) { this.dueInDays = dueInDays; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
