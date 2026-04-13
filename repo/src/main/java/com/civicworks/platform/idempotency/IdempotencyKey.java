package com.civicworks.platform.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"scope", "idem_key"})
})
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope", nullable = false, length = 50)
    private String scope;

    @Column(name = "idem_key", nullable = false, length = 200)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_snapshot", columnDefinition = "text")
    private String responseSnapshot;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum Status {
        PENDING, COMPLETED
    }

    public Long getId() { return id; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getResponseSnapshot() { return responseSnapshot; }
    public void setResponseSnapshot(String responseSnapshot) { this.responseSnapshot = responseSnapshot; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
