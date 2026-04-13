package com.civicworks.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20) private String channel;
    @Column(name = "recipient_ref", nullable = false, length = 500) private String recipientRef;
    @Column(length = 500) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "payload_json", columnDefinition = "text") private String payloadJson;
    @Column(nullable = false) private boolean exported = false;
    @Column(name = "exported_at") private Instant exportedAt;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String recipientRef) { this.recipientRef = recipientRef; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public boolean isExported() { return exported; }
    public void setExported(boolean exported) { this.exported = exported; }
    public Instant getExportedAt() { return exportedAt; }
    public void setExportedAt(Instant exportedAt) { this.exportedAt = exportedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
