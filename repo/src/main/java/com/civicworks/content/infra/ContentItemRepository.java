package com.civicworks.content.infra;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.domain.ContentItem.ContentState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {

    @Query("SELECT c FROM ContentItem c WHERE c.state = 'PUBLISHED' AND c.publishedAt <= :now " +
            "AND (:type IS NULL OR c.contentType = :type) " +
            "AND (:origin IS NULL OR c.origin = :origin)")
    Page<ContentItem> findPublished(
            @Param("now") Instant now,
            @Param("type") ContentItem.ContentType type,
            @Param("origin") String origin,
            Pageable pageable);

    @Query("SELECT c FROM ContentItem c WHERE c.state = 'PUBLISHED' AND c.publishedAt <= :now AND c.id = :id")
    Optional<ContentItem> findPublishedById(@Param("id") Long id, @Param("now") Instant now);

    @Query("SELECT c FROM ContentItem c WHERE (:state IS NULL OR c.state = :state)")
    Page<ContentItem> findAllFiltered(@Param("state") ContentState state, Pageable pageable);

    @Query("SELECT c FROM ContentItem c WHERE c.state = 'SCHEDULED' AND c.scheduledAt <= :now")
    List<ContentItem> findReadyToPublish(@Param("now") Instant now);
}
