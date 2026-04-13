package com.civicworks.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cash_shifts")
public class CashShift {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operator_id", nullable = false) private Long operatorId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(nullable = false, length = 20) private String status = "OPEN";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
