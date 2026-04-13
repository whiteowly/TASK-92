package com.civicworks.searchanalytics.application;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.searchanalytics.domain.SearchDocument;
import com.civicworks.searchanalytics.infra.SearchDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexService {

    private final SearchDocumentRepository searchDocRepo;

    public SearchIndexService(SearchDocumentRepository searchDocRepo) {
        this.searchDocRepo = searchDocRepo;
    }

    @Transactional
    public void indexContent(ContentItem item) {
        SearchDocument doc = searchDocRepo.findByRecordTypeAndRecordId(
                item.getContentType().name(), item.getId()).orElse(new SearchDocument());

        doc.setRecordType(item.getContentType().name());
        doc.setRecordId(item.getId());
        doc.setTitle(item.getTitle());
        doc.setBody(item.getSanitizedBody());
        doc.setOrigin(item.getOrigin());
        doc.setPrice(item.getPrice());
        doc.setState(item.getState().name());
        doc.setPublishedAt(item.getPublishedAt());

        // Category from first tag
        if (item.getTags() != null && !item.getTags().isEmpty()) {
            doc.setCategory(item.getTags().iterator().next().getName());
        }

        searchDocRepo.save(doc);
    }
}
