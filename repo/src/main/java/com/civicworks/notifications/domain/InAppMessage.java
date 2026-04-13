package com.civicworks.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "in_app_messages")
public class InAppMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_id", nullable = false) private Long recipientId;
    @Column(name = "template_id") private Long templateId;
    @Column(length = 500) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
