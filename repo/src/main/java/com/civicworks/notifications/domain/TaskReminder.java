package com.civicworks.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_reminders")
public class TaskReminder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Column(name = "entity_type", length = 100) private String entityType;
    @Column(name = "entity_id", length = 200) private String entityId;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(nullable = false) private boolean sent = false;
    @Column(name = "retry_count", nullable = false) private int retryCount = 0;
    @Column(name = "max_retries", nullable = false) private int maxRetries = 3;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Instant getCreatedAt() { return createdAt; }
}
