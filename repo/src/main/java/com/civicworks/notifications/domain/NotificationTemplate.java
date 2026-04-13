package com.civicworks.notifications.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 500) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(nullable = false, length = 20) private String channel = "IN_APP";
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @PrePersist void pre() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void upd() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
