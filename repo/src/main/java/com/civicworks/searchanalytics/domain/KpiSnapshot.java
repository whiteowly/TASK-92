package com.civicworks.searchanalytics.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "kpi_snapshots")
public class KpiSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;
    @Column(name = "metric_name", nullable = false, length = 200) private String metricName;
    @Column(name = "metric_value", nullable = false, precision = 14, scale = 4) private BigDecimal metricValue;
    @Column(name = "metadata_json", columnDefinition = "text") private String metadataJson;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
