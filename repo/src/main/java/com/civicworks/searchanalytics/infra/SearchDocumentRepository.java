package com.civicworks.searchanalytics.infra;

import com.civicworks.searchanalytics.domain.SearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, Long> {

    Optional<SearchDocument> findByRecordTypeAndRecordId(String recordType, Long recordId);

    @Query(value = "SELECT sd.* FROM search_documents sd " +
            "WHERE sd.state = 'PUBLISHED' AND sd.published_at <= now() " +
            "AND (:query IS NULL OR sd.tsv @@ plainto_tsquery('english', :query)) " +
            "AND (:recordType IS NULL OR sd.record_type = :recordType) " +
            "AND (:category IS NULL OR sd.category = :category) " +
            "AND (:origin IS NULL OR sd.origin = :origin) " +
            "AND (:minPrice IS NULL OR sd.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR sd.price <= :maxPrice) " +
            "ORDER BY CASE WHEN :sort = 'NEWEST' THEN extract(epoch from sd.published_at) * -1 " +
            "WHEN :sort = 'PRICE_ASC' THEN COALESCE(sd.price, 0) " +
            "WHEN :sort = 'PRICE_DESC' THEN COALESCE(sd.price, 0) * -1 " +
            "ELSE 0 END, " +
            "CASE WHEN :query IS NOT NULL THEN ts_rank(sd.tsv, plainto_tsquery('english', :query)) ELSE 0 END DESC",
            countQuery = "SELECT count(*) FROM search_documents sd " +
                    "WHERE sd.state = 'PUBLISHED' AND sd.published_at <= now() " +
                    "AND (:query IS NULL OR sd.tsv @@ plainto_tsquery('english', :query)) " +
                    "AND (:recordType IS NULL OR sd.record_type = :recordType) " +
                    "AND (:category IS NULL OR sd.category = :category) " +
                    "AND (:origin IS NULL OR sd.origin = :origin) " +
                    "AND (:minPrice IS NULL OR sd.price >= :minPrice) " +
                    "AND (:maxPrice IS NULL OR sd.price <= :maxPrice)",
            nativeQuery = true)
    Page<SearchDocument> searchPublic(@Param("query") String query,
                                      @Param("recordType") String recordType,
                                      @Param("category") String category,
                                      @Param("origin") String origin,
                                      @Param("minPrice") BigDecimal minPrice,
                                      @Param("maxPrice") BigDecimal maxPrice,
                                      @Param("sort") String sort,
                                      Pageable pageable);

    @Query(value = "SELECT sd.* FROM search_documents sd " +
            "WHERE (:query IS NULL OR sd.tsv @@ plainto_tsquery('english', :query)) " +
            "AND (:recordType IS NULL OR sd.record_type = :recordType) " +
            "AND (:category IS NULL OR sd.category = :category) " +
            "AND (:origin IS NULL OR sd.origin = :origin) " +
            "AND (:minPrice IS NULL OR sd.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR sd.price <= :maxPrice) " +
            "ORDER BY CASE WHEN :query IS NOT NULL THEN ts_rank(sd.tsv, plainto_tsquery('english', :query)) ELSE 0 END DESC",
            countQuery = "SELECT count(*) FROM search_documents sd " +
                    "WHERE (:query IS NULL OR sd.tsv @@ plainto_tsquery('english', :query)) " +
                    "AND (:recordType IS NULL OR sd.record_type = :recordType) " +
                    "AND (:category IS NULL OR sd.category = :category) " +
                    "AND (:origin IS NULL OR sd.origin = :origin) " +
                    "AND (:minPrice IS NULL OR sd.price >= :minPrice) " +
                    "AND (:maxPrice IS NULL OR sd.price <= :maxPrice)",
            nativeQuery = true)
    Page<SearchDocument> searchAuthenticated(@Param("query") String query,
                                              @Param("recordType") String recordType,
                                              @Param("category") String category,
                                              @Param("origin") String origin,
                                              @Param("minPrice") BigDecimal minPrice,
                                              @Param("maxPrice") BigDecimal maxPrice,
                                              Pageable pageable);

    @Query(value = "SELECT DISTINCT title FROM search_documents " +
            "WHERE state = 'PUBLISHED' AND title % :prefix ORDER BY similarity(title, :prefix) DESC LIMIT 10",
            nativeQuery = true)
    java.util.List<String> typeaheadPublic(@Param("prefix") String prefix);
}
