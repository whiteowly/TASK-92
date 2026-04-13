package com.civicworks.dispatch.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "zone_capacity_rules")
public class ZoneCapacityRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "zone_id", nullable = false, unique = true)
    private Long zoneId;
    @Column(name = "max_concurrent_assignments", nullable = false)
    private int maxConcurrentAssignments = 10;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @PrePersist @PreUpdate
    void onSave() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public int getMaxConcurrentAssignments() { return maxConcurrentAssignments; }
    public void setMaxConcurrentAssignments(int max) { this.maxConcurrentAssignments = max; }
    public Instant getUpdatedAt() { return updatedAt; }
}
