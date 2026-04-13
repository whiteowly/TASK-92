package com.civicworks.searchanalytics.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "search_documents", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"record_type", "record_id"})
})
public class SearchDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "record_type", nullable = false, length = 50) private String recordType;
    @Column(name = "record_id", nullable = false) private Long recordId;
    @Column(nullable = false, length = 500) private String title;
    @Column(columnDefinition = "text") private String body;
    @Column(length = 200) private String category;
    @Column(length = 200) private String origin;
    @Column(precision = 12, scale = 2) private BigDecimal price;
    @Column(length = 30) private String state;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "updated_at") private Instant updatedAt;

    @PrePersist @PreUpdate
    void onSave() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
