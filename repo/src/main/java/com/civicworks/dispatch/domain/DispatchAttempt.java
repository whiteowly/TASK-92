package com.civicworks.dispatch.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dispatch_attempts")
public class DispatchAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "driver_id", nullable = false)
    private Long driverId;
    @Column(nullable = false)
    private boolean forced = false;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "rejection_reason", length = 100)
    private String rejectionReason;
    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    void pre() { attemptedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public boolean isForced() { return forced; }
    public void setForced(boolean forced) { this.forced = forced; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
