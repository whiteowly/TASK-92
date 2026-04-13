package com.civicworks.content.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "content_items")
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "sanitized_body", columnDefinition = "text")
    private String sanitizedBody;

    @Column(name = "content_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ContentState state = ContentState.DRAFT;

    @Column(length = 200)
    private String origin;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "unpublished_at")
    private Instant unpublishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "content_item_tag",
            joinColumns = @JoinColumn(name = "content_item_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<ContentTag> tags = new HashSet<>();

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getSanitizedBody() { return sanitizedBody; }
    public void setSanitizedBody(String sanitizedBody) { this.sanitizedBody = sanitizedBody; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public ContentState getState() { return state; }
    public void setState(ContentState state) { this.state = state; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getUnpublishedAt() { return unpublishedAt; }
    public void setUnpublishedAt(Instant unpublishedAt) { this.unpublishedAt = unpublishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public Set<ContentTag> getTags() { return tags; }
    public void setTags(Set<ContentTag> tags) { this.tags = tags; }

    public enum ContentType { NEWS, POLICY, EVENT, CLASS }
    public enum ContentState { DRAFT, SCHEDULED, PUBLISHED, UNPUBLISHED }
}
