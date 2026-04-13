package com.civicworks.searchanalytics.application;

import com.civicworks.searchanalytics.domain.SearchDocument;
import com.civicworks.searchanalytics.domain.SearchHistory;
import com.civicworks.searchanalytics.infra.SearchDocumentRepository;
import com.civicworks.searchanalytics.infra.SearchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SearchService {

    private final SearchDocumentRepository searchDocRepo;
    private final SearchHistoryRepository historyRepo;

    public SearchService(SearchDocumentRepository searchDocRepo, SearchHistoryRepository historyRepo) {
        this.searchDocRepo = searchDocRepo;
        this.historyRepo = historyRepo;
    }

    public Page<SearchDocument> searchPublic(String q, String recordType, String category,
                                              String origin, BigDecimal minPrice, BigDecimal maxPrice,
                                              String sort, int page, int size) {
        return searchDocRepo.searchPublic(q, recordType, category, origin, minPrice, maxPrice,
                sort != null ? sort : "RELEVANCE",
                PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional
    public Page<SearchDocument> searchAuthenticated(String q, String recordType, String category,
                                                     String origin, BigDecimal minPrice, BigDecimal maxPrice,
                                                     String sort, int page, int size, Long userId) {
        Page<SearchDocument> results = searchDocRepo.searchAuthenticated(
                q, recordType, category, origin, minPrice, maxPrice,
                PageRequest.of(page, Math.min(size, 100)));

        if (q != null && !q.isBlank() && userId != null) {
            SearchHistory sh = new SearchHistory();
            sh.setUserId(userId);
            sh.setQueryText(q);
            sh.setResultCount((int) results.getTotalElements());
            historyRepo.save(sh);
        }

        return results;
    }

    public List<String> typeaheadPublic(String prefix) {
        if (prefix == null || prefix.length() < 2) return List.of();
        return searchDocRepo.typeaheadPublic(prefix);
    }

    public List<String> typeaheadAuthenticated(String prefix, Long userId) {
        List<String> recent = historyRepo.findRecentQueries(userId);
        if (prefix != null && prefix.length() >= 2) {
            String lower = prefix.toLowerCase();
            return recent.stream().filter(q -> q.toLowerCase().contains(lower)).limit(10).toList();
        }
        return recent.stream().limit(10).toList();
    }

    public List<SearchDocument> recommendationsPublic() {
        // Non-personalized: return newest published content
        return searchDocRepo.searchPublic(null, null, null, null, null, null, "NEWEST",
                PageRequest.of(0, 10)).getContent();
    }

    public List<SearchDocument> recommendationsAuthenticated(Long userId) {
        // Personalized: use user's recent search categories
        List<String> recentQueries = historyRepo.findRecentQueries(userId);
        if (!recentQueries.isEmpty()) {
            String q = recentQueries.getFirst();
            return searchDocRepo.searchAuthenticated(q, null, null, null, null, null,
                    PageRequest.of(0, 10)).getContent();
        }
        return recommendationsPublic();
    }

    public List<SearchHistory> getHistory(Long userId) {
        return historyRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteHistory(Long userId) {
        historyRepo.deleteByUserId(userId);
    }

    @Transactional
    public int purgeOldHistory(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return historyRepo.deleteOlderThan(cutoff);
    }
}
