package com.civicworks.dispatch.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dispatch_orders")
public class DispatchOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "zone_id")
    private Long zoneId;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false, length = 30)
    private String status = "PENDING";
    @Column(nullable = false)
    private int priority = 0;
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "assigned_driver_id")
    private Long assignedDriverId;
    @Column(nullable = false)
    private boolean forced = false;
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private RejectionReason rejectionReason;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private Long version;

    @PrePersist
    void pre() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void upd() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public Long getAssignedDriverId() { return assignedDriverId; }
    public void setAssignedDriverId(Long assignedDriverId) { this.assignedDriverId = assignedDriverId; }
    public boolean isForced() { return forced; }
    public void setForced(boolean forced) { this.forced = forced; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(RejectionReason rejectionReason) { this.rejectionReason = rejectionReason; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
