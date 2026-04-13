package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "discrepancy_cases")
public class DiscrepancyCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "handover_report_id", nullable = false) private Long handoverReportId;
    @Column(name = "variance_amount", nullable = false, precision = 12, scale = 2) private BigDecimal varianceAmount;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(name = "resolution_notes", columnDefinition = "text") private String resolutionNotes;
    @Column(name = "resolved_by") private Long resolvedBy;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getHandoverReportId() { return handoverReportId; }
    public void setHandoverReportId(Long handoverReportId) { this.handoverReportId = handoverReportId; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
