package com.civicworks.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "delivery_receipts")
public class DeliveryReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "message_id", nullable = false) private Long messageId;
    @Column(nullable = false, length = 20) private String status; // DELIVERED, READ, ACKNOWLEDGED
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
