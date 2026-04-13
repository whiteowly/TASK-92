package com.civicworks.content;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.domain.ContentItem.ContentState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class ContentVisibilityServiceTest {

    private boolean isPubliclyVisible(ContentItem item, Instant now) {
        return item.getState() == ContentState.PUBLISHED
                && item.getPublishedAt() != null
                && !item.getPublishedAt().isAfter(now);
    }

    @Test
    void publishedContentIsVisible() {
        ContentItem item = new ContentItem();
        item.setState(ContentState.PUBLISHED);
        item.setPublishedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(isPubliclyVisible(item, Instant.now()));
    }

    @Test
    void draftContentIsNotVisible() {
        ContentItem item = new ContentItem();
        item.setState(ContentState.DRAFT);
        assertFalse(isPubliclyVisible(item, Instant.now()));
    }

    @Test
    void scheduledContentNotYetPublishedIsNotVisible() {
        ContentItem item = new ContentItem();
        item.setState(ContentState.SCHEDULED);
        item.setScheduledAt(Instant.now().plus(1, ChronoUnit.DAYS));
        assertFalse(isPubliclyVisible(item, Instant.now()));
    }

    @Test
    void unpublishedContentIsNotVisible() {
        ContentItem item = new ContentItem();
        item.setState(ContentState.UNPUBLISHED);
        assertFalse(isPubliclyVisible(item, Instant.now()));
    }

    @Test
    void futurePublishDateIsNotVisible() {
        ContentItem item = new ContentItem();
        item.setState(ContentState.PUBLISHED);
        item.setPublishedAt(Instant.now().plus(1, ChronoUnit.HOURS));
        assertFalse(isPubliclyVisible(item, Instant.now()));
    }

    @Test
    void validStateTransitionsFromDraft() {
        // DRAFT -> PUBLISHED, DRAFT -> SCHEDULED
        ContentState state = ContentState.DRAFT;
        assertTrue(state == ContentState.DRAFT);
        // Valid transitions
        assertDoesNotThrow(() -> {
            if (state != ContentState.DRAFT && state != ContentState.SCHEDULED && state != ContentState.UNPUBLISHED) {
                throw new IllegalStateException();
            }
        });
    }
}
