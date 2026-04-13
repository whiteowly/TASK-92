package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "bill_id", nullable = false) private Long billId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private PaymentMethod method;
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 20) private SettlementType settlementType = SettlementType.FULL;
    @Column(length = 200) private String reference;
    @Column(name = "shift_id") private Long shiftId;
    @Column(name = "posted_by") private Long postedBy;
    @Column(nullable = false) private boolean reversed = false;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    // Business-meaningful payment timestamp: when the cash/check/etc. was
    // physically received at the counter. Distinct from created_at (the row
    // insert time) so reporting can age payments by the real receipt moment.
    // Defaults to the row's createdAt at insert time so existing call sites
    // remain correct without code changes.
    @Column(name = "received_at") private Instant receivedAt;

    @PrePersist void pre() {
        createdAt = Instant.now();
        if (receivedAt == null) {
            receivedAt = createdAt;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public SettlementType getSettlementType() { return settlementType; }
    public void setSettlementType(SettlementType settlementType) { this.settlementType = settlementType; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public Long getPostedBy() { return postedBy; }
    public void setPostedBy(Long postedBy) { this.postedBy = postedBy; }
    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
