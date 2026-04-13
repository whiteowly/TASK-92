package com.civicworks.content.application;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.domain.ContentItem.ContentState;
import com.civicworks.content.domain.ContentItem.ContentType;
import com.civicworks.content.domain.ContentPublishHistory;
import com.civicworks.content.domain.ContentTag;
import com.civicworks.content.infra.ContentItemRepository;
import com.civicworks.content.infra.ContentPublishHistoryRepository;
import com.civicworks.content.infra.ContentTagRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import com.civicworks.searchanalytics.application.SearchIndexService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContentService {

    private final ContentItemRepository contentRepo;
    private final ContentTagRepository tagRepo;
    private final ContentPublishHistoryRepository historyRepo;
    private final SearchIndexService searchIndexService;
    private final AuditService auditService;
    private final MunicipalClock clock;

    public ContentService(ContentItemRepository contentRepo,
                          ContentTagRepository tagRepo,
                          ContentPublishHistoryRepository historyRepo,
                          SearchIndexService searchIndexService,
                          AuditService auditService,
                          MunicipalClock clock) {
        this.contentRepo = contentRepo;
        this.tagRepo = tagRepo;
        this.historyRepo = historyRepo;
        this.searchIndexService = searchIndexService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ContentItem> listPublished(ContentType type, String origin, int page, int size) {
        Instant now = clock.instant();
        return contentRepo.findPublished(now, type, origin, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public ContentItem getPublished(Long id) {
        return contentRepo.findPublishedById(id, clock.instant())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_PUBLIC,
                        "Content not found or not publicly available",
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<ContentItem> listAll(ContentState state, int page, int size) {
        return contentRepo.findAllFiltered(state, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public ContentItem getById(Long id) {
        return contentRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Content item not found"));
    }

    @Transactional
    public ContentItem create(ContentItem item, Set<String> tagNames, Long actorId) {
        item.setSanitizedBody(sanitizeHtml(item.getBody()));
        item.setCreatedBy(actorId);
        if (tagNames != null) {
            item.setTags(resolveTags(tagNames));
        }
        ContentItem saved = contentRepo.save(item);
        searchIndexService.indexContent(saved);
        auditService.log(actorId, "CONTENT_EDITOR", "CONTENT_CREATE", "content_item",
                saved.getId().toString(), null);
        return saved;
    }

    @Transactional
    public ContentItem update(Long id, ContentItem updates, Set<String> tagNames, Long actorId) {
        ContentItem existing = getById(id);
        existing.setTitle(updates.getTitle());
        existing.setBody(updates.getBody());
        existing.setSanitizedBody(sanitizeHtml(updates.getBody()));
        existing.setContentType(updates.getContentType());
        existing.setOrigin(updates.getOrigin());
        existing.setPrice(updates.getPrice());
        if (updates.getScheduledAt() != null) {
            existing.setScheduledAt(updates.getScheduledAt());
        }
        if (tagNames != null) {
            existing.setTags(resolveTags(tagNames));
        }
        ContentItem saved = contentRepo.save(existing);
        searchIndexService.indexContent(saved);
        auditService.log(actorId, "CONTENT_EDITOR", "CONTENT_UPDATE", "content_item",
                id.toString(), null);
        return saved;
    }

    @Transactional
    public ContentItem publish(Long id, Long actorId) {
        ContentItem item = getById(id);
        ContentState prev = item.getState();
        if (prev != ContentState.DRAFT && prev != ContentState.SCHEDULED && prev != ContentState.UNPUBLISHED) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot publish from state: " + prev);
        }
        item.setState(ContentState.PUBLISHED);
        item.setPublishedAt(clock.instant());
        ContentItem saved = contentRepo.save(item);

        recordHistory(id, "PUBLISH", actorId, prev.name(), ContentState.PUBLISHED.name());
        searchIndexService.indexContent(saved);
        auditService.log(actorId, "CONTENT_EDITOR", "CONTENT_PUBLISH", "content_item",
                id.toString(), "state:" + prev + "->PUBLISHED");
        return saved;
    }

    @Transactional
    public ContentItem unpublish(Long id, Long actorId) {
        ContentItem item = getById(id);
        ContentState prev = item.getState();
        if (prev != ContentState.PUBLISHED) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot unpublish from state: " + prev);
        }
        item.setState(ContentState.UNPUBLISHED);
        item.setUnpublishedAt(clock.instant());
        ContentItem saved = contentRepo.save(item);

        recordHistory(id, "UNPUBLISH", actorId, prev.name(), ContentState.UNPUBLISHED.name());
        searchIndexService.indexContent(saved);
        auditService.log(actorId, "CONTENT_EDITOR", "CONTENT_UNPUBLISH", "content_item",
                id.toString(), "state:" + prev + "->UNPUBLISHED");
        return saved;
    }

    @Transactional
    public void processScheduledPublications() {
        List<ContentItem> ready = contentRepo.findReadyToPublish(clock.instant());
        for (ContentItem item : ready) {
            item.setState(ContentState.PUBLISHED);
            item.setPublishedAt(clock.instant());
            contentRepo.save(item);
            recordHistory(item.getId(), "SCHEDULE_PUBLISH", item.getCreatedBy(),
                    ContentState.SCHEDULED.name(), ContentState.PUBLISHED.name());
            searchIndexService.indexContent(item);
        }
    }

    public List<ContentPublishHistory> getPublishHistory(Long contentItemId) {
        return historyRepo.findByContentItemIdOrderByCreatedAtDesc(contentItemId);
    }

    private void recordHistory(Long contentItemId, String action, Long actorId,
                               String prevState, String newState) {
        ContentPublishHistory h = new ContentPublishHistory();
        h.setContentItemId(contentItemId);
        h.setAction(action);
        h.setActorId(actorId);
        h.setPreviousState(prevState);
        h.setNewState(newState);
        historyRepo.save(h);
    }

    private Set<ContentTag> resolveTags(Set<String> tagNames) {
        Set<ContentTag> tags = new HashSet<>();
        for (String name : tagNames) {
            ContentTag tag = tagRepo.findByName(name).orElseGet(() -> {
                ContentTag t = new ContentTag();
                t.setName(name);
                return tagRepo.save(t);
            });
            tags.add(tag);
        }
        return tags;
    }

    /**
     * Allowlist HTML sanitization backed by jsoup. Drops scripts, event
     * handlers, javascript: URIs, and any tag/attribute not on the relaxed
     * allowlist. Output is sanitized HTML (not plain text) per the prompt.
     */
    private static final Safelist SAFELIST = Safelist.relaxed();

    static String sanitizeHtml(String html) {
        if (html == null) return null;
        // prettyPrint(false) keeps whitespace stable and avoids reflowing.
        return Jsoup.clean(html, "", SAFELIST,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }
}
