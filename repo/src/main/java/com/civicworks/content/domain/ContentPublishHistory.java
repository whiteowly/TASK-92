package com.civicworks.content.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_publish_history")
public class ContentPublishHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_item_id", nullable = false)
    private Long contentItemId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "previous_state", length = 30)
    private String previousState;

    @Column(name = "new_state", nullable = false, length = 30)
    private String newState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getContentItemId() { return contentItemId; }
    public void setContentItemId(Long contentItemId) { this.contentItemId = contentItemId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }
    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }
    public Instant getCreatedAt() { return createdAt; }
}
