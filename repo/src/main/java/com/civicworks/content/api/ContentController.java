package com.civicworks.content.api;

import com.civicworks.content.application.ContentService;
import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.domain.ContentItem.ContentState;
import com.civicworks.content.domain.ContentItem.ContentType;
import com.civicworks.content.domain.ContentPublishHistory;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    // --- Public endpoints ---

    @GetMapping("/public/content-items")
    public ResponseEntity<Page<ContentItemDto>> listPublished(
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String origin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContentItem> items = contentService.listPublished(type, origin, page, size);
        return ResponseEntity.ok(items.map(ContentItemDto::from));
    }

    @GetMapping("/public/content-items/{id}")
    public ResponseEntity<ContentItemDto> getPublished(@PathVariable Long id) {
        return ResponseEntity.ok(ContentItemDto.from(contentService.getPublished(id)));
    }

    // --- Internal management endpoints ---

    @PostMapping("/content-items")
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    public ResponseEntity<ContentItemDto> create(@Valid @RequestBody CreateContentRequest request) {
        ContentItem item = new ContentItem();
        item.setTitle(request.title());
        item.setBody(request.body());
        item.setContentType(request.contentType());
        item.setOrigin(request.origin());
        item.setPrice(request.price());
        item.setScheduledAt(request.scheduledAt());
        if (request.scheduledAt() != null) {
            item.setState(ContentState.SCHEDULED);
        }
        ContentItem saved = contentService.create(item, request.tags(), SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentItemDto.from(saved));
    }

    @GetMapping("/content-items")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'SYSTEM_ADMIN', 'AUDITOR')")
    public ResponseEntity<Page<ContentItemDto>> listAll(
            @RequestParam(required = false) ContentState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(contentService.listAll(state, page, size).map(ContentItemDto::from));
    }

    @GetMapping("/content-items/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'SYSTEM_ADMIN', 'AUDITOR')")
    public ResponseEntity<ContentItemDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ContentItemDto.from(contentService.getById(id)));
    }

    @PutMapping("/content-items/{id}")
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    public ResponseEntity<ContentItemDto> update(@PathVariable Long id,
                                                  @Valid @RequestBody CreateContentRequest request) {
        ContentItem updates = new ContentItem();
        updates.setTitle(request.title());
        updates.setBody(request.body());
        updates.setContentType(request.contentType());
        updates.setOrigin(request.origin());
        updates.setPrice(request.price());
        updates.setScheduledAt(request.scheduledAt());
        ContentItem saved = contentService.update(id, updates, request.tags(), SecurityUtils.currentUserId());
        return ResponseEntity.ok(ContentItemDto.from(saved));
    }

    @PostMapping("/content-items/{id}/publish")
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    public ResponseEntity<ContentItemDto> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ContentItemDto.from(
                contentService.publish(id, SecurityUtils.currentUserId())));
    }

    @PostMapping("/content-items/{id}/unpublish")
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    public ResponseEntity<ContentItemDto> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(ContentItemDto.from(
                contentService.unpublish(id, SecurityUtils.currentUserId())));
    }

    @GetMapping("/content-items/{id}/publish-history")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'SYSTEM_ADMIN', 'AUDITOR')")
    public ResponseEntity<List<ContentPublishHistory>> getPublishHistory(@PathVariable Long id) {
        return ResponseEntity.ok(contentService.getPublishHistory(id));
    }

    public record CreateContentRequest(
            @NotBlank String title,
            String body,
            @NotNull ContentType contentType,
            String origin,
            BigDecimal price,
            Instant scheduledAt,
            Set<String> tags
    ) {}

    public record ContentItemDto(
            Long id, String title, String sanitizedBody, ContentType contentType,
            ContentState state, String origin, BigDecimal price,
            Instant scheduledAt, Instant publishedAt, Instant unpublishedAt,
            Long createdBy, Instant createdAt, Long version
    ) {
        static ContentItemDto from(ContentItem item) {
            return new ContentItemDto(item.getId(), item.getTitle(), item.getSanitizedBody(),
                    item.getContentType(), item.getState(), item.getOrigin(), item.getPrice(),
                    item.getScheduledAt(), item.getPublishedAt(), item.getUnpublishedAt(),
                    item.getCreatedBy(), item.getCreatedAt(), item.getVersion());
        }
    }
}
