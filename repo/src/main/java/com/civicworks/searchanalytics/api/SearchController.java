package com.civicworks.searchanalytics.api;

import com.civicworks.platform.security.SecurityUtils;
import com.civicworks.searchanalytics.application.SearchService;
import com.civicworks.searchanalytics.domain.SearchDocument;
import com.civicworks.searchanalytics.domain.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    // Public search
    @GetMapping("/public/search")
    public ResponseEntity<Page<SearchDocument>> searchPublic(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String recordType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(searchService.searchPublic(
                q, recordType, category, origin, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/public/search/typeahead")
    public ResponseEntity<List<String>> typeaheadPublic(@RequestParam String q) {
        return ResponseEntity.ok(searchService.typeaheadPublic(q));
    }

    @GetMapping("/public/search/recommendations")
    public ResponseEntity<List<SearchDocument>> recommendationsPublic() {
        return ResponseEntity.ok(searchService.recommendationsPublic());
    }

    // Authenticated search
    @GetMapping("/search")
    public ResponseEntity<Page<SearchDocument>> searchAuthenticated(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String recordType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(searchService.searchAuthenticated(
                q, recordType, category, origin, minPrice, maxPrice, sort, page, size,
                SecurityUtils.currentUserId()));
    }

    @GetMapping("/search/typeahead")
    public ResponseEntity<List<String>> typeaheadAuthenticated(@RequestParam String q) {
        return ResponseEntity.ok(searchService.typeaheadAuthenticated(q, SecurityUtils.currentUserId()));
    }

    @GetMapping("/search/recommendations")
    public ResponseEntity<List<SearchDocument>> recommendationsAuthenticated() {
        return ResponseEntity.ok(searchService.recommendationsAuthenticated(SecurityUtils.currentUserId()));
    }

    @GetMapping("/search/history")
    public ResponseEntity<List<SearchHistory>> getHistory() {
        return ResponseEntity.ok(searchService.getHistory(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/search/history")
    public ResponseEntity<Void> deleteHistory() {
        searchService.deleteHistory(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
