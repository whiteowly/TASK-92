package com.civicworks.searchanalytics.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "search_history")
public class SearchHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "query_text", nullable = false, length = 500) private String queryText;
    @Column(name = "result_count") private Integer resultCount;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void pre() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
    public Instant getCreatedAt() { return createdAt; }
}
