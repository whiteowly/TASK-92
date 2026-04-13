package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_reversals")
public class PaymentReversal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "original_payment_id", nullable = false) private Long originalPaymentId;
    @Column(name = "reversal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal reversalAmount;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "reversed_by") private Long reversedBy;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long originalPaymentId) { this.originalPaymentId = originalPaymentId; }
    public BigDecimal getReversalAmount() { return reversalAmount; }
    public void setReversalAmount(BigDecimal reversalAmount) { this.reversalAmount = reversalAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getReversedBy() { return reversedBy; }
    public void setReversedBy(Long reversedBy) { this.reversedBy = reversedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
