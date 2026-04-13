package com.civicworks.searchanalytics.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "anomaly_flags")
public class AnomalyFlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "flag_type", nullable = false, length = 100) private String flagType;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "metric_value", precision = 14, scale = 4) private BigDecimal metricValue;
    @Column(name = "threshold_value", precision = 14, scale = 4) private BigDecimal thresholdValue;
    @Column(nullable = false) private boolean acknowledged = false;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getFlagType() { return flagType; }
    public void setFlagType(String flagType) { this.flagType = flagType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    public Instant getCreatedAt() { return createdAt; }
}
