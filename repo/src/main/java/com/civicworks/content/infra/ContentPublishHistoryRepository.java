package com.civicworks.content.infra;

import com.civicworks.content.domain.ContentPublishHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContentPublishHistoryRepository extends JpaRepository<ContentPublishHistory, Long> {
    List<ContentPublishHistory> findByContentItemIdOrderByCreatedAtDesc(Long contentItemId);
}
