package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(name = "line_item_id") private Long lineItemId;
    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;
    @Column(name = "allocation_order", nullable = false) private int allocationOrder;
    @Column(name = "residual_cents", nullable = false) private int residualCents = 0;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getLineItemId() { return lineItemId; }
    public void setLineItemId(Long lineItemId) { this.lineItemId = lineItemId; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public int getAllocationOrder() { return allocationOrder; }
    public void setAllocationOrder(int allocationOrder) { this.allocationOrder = allocationOrder; }
    public int getResidualCents() { return residualCents; }
    public void setResidualCents(int residualCents) { this.residualCents = residualCents; }
}
