package com.civicworks.searchanalytics.infra;

import com.civicworks.searchanalytics.domain.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT sh.queryText FROM SearchHistory sh WHERE sh.userId = :userId " +
            "GROUP BY sh.queryText ORDER BY MAX(sh.createdAt) DESC")
    List<String> findRecentQueries(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
