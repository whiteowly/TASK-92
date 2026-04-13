package com.civicworks.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_item_id", nullable = false)
    private Long contentItemId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "filter_hit_count", nullable = false)
    private int filterHitCount = 0;

    @Column(name = "moderation_state", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ModerationState moderationState = ModerationState.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public enum ModerationState { PENDING, APPROVED, REJECTED, HOLD_FOR_REVIEW, ESCALATED }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getContentItemId() { return contentItemId; }
    public void setContentItemId(Long contentItemId) { this.contentItemId = contentItemId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public int getFilterHitCount() { return filterHitCount; }
    public void setFilterHitCount(int filterHitCount) { this.filterHitCount = filterHitCount; }
    public ModerationState getModerationState() { return moderationState; }
    public void setModerationState(ModerationState moderationState) { this.moderationState = moderationState; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
