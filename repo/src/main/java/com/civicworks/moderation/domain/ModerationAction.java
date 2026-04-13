package com.civicworks.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_actions")
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(nullable = false, length = 30)
    private String action; // APPROVE, REJECT, ESCALATE

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
}
