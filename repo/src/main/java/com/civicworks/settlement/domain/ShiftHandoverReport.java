package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shift_handover_reports")
public class ShiftHandoverReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "shift_id", nullable = false) private Long shiftId;
    @Column(name = "cash_total", nullable = false, precision = 12, scale = 2) private BigDecimal cashTotal = BigDecimal.ZERO;
    @Column(name = "check_total", nullable = false, precision = 12, scale = 2) private BigDecimal checkTotal = BigDecimal.ZERO;
    @Column(name = "voucher_total", nullable = false, precision = 12, scale = 2) private BigDecimal voucherTotal = BigDecimal.ZERO;
    @Column(name = "other_total", nullable = false, precision = 12, scale = 2) private BigDecimal otherTotal = BigDecimal.ZERO;
    @Column(name = "posted_ar_total", nullable = false, precision = 12, scale = 2) private BigDecimal postedArTotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal variance = BigDecimal.ZERO;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public BigDecimal getCashTotal() { return cashTotal; }
    public void setCashTotal(BigDecimal cashTotal) { this.cashTotal = cashTotal; }
    public BigDecimal getCheckTotal() { return checkTotal; }
    public void setCheckTotal(BigDecimal checkTotal) { this.checkTotal = checkTotal; }
    public BigDecimal getVoucherTotal() { return voucherTotal; }
    public void setVoucherTotal(BigDecimal voucherTotal) { this.voucherTotal = voucherTotal; }
    public BigDecimal getOtherTotal() { return otherTotal; }
    public void setOtherTotal(BigDecimal otherTotal) { this.otherTotal = otherTotal; }
    public BigDecimal getPostedArTotal() { return postedArTotal; }
    public void setPostedArTotal(BigDecimal postedArTotal) { this.postedArTotal = postedArTotal; }
    public BigDecimal getVariance() { return variance; }
    public void setVariance(BigDecimal variance) { this.variance = variance; }
    public Instant getCreatedAt() { return createdAt; }
}
