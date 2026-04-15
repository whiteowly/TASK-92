package com.civicworks.moderation;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.infra.ContentItemRepository;
import com.civicworks.moderation.application.CommentFilterService;
import com.civicworks.moderation.application.ModerationService;
import com.civicworks.moderation.domain.Comment;
import com.civicworks.moderation.domain.Comment.ModerationState;
import com.civicworks.moderation.domain.ModerationAction;
import com.civicworks.moderation.domain.SensitiveWord;
import com.civicworks.moderation.infra.CommentRepository;
import com.civicworks.moderation.infra.ModerationActionRepository;
import com.civicworks.moderation.infra.SensitiveWordRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct service-level coverage for ModerationService. ModerationApiIT
 * exercises the HTTP layer; this test isolates the rules: hold-for-review
 * threshold, action → state mapping, sensitive-word lifecycle, and audit
 * logging side effects.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private CommentRepository commentRepo;
    @Mock private ModerationActionRepository actionRepo;
    @Mock private SensitiveWordRepository wordRepo;
    @Mock private ContentItemRepository contentRepo;
    @Mock private CommentFilterService filterService;
    @Mock private AuditService auditService;
    @Mock private MunicipalClock clock;

    private ModerationService svc;

    @BeforeEach
    void setUp() {
        svc = new ModerationService(commentRepo, actionRepo, wordRepo, contentRepo,
                filterService, auditService, clock);
        // Lenient: not all tests exercise these collaborators (sensitive-word
        // tests don't touch the clock or comment repo).
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-04-15T12:00:00Z"));
        lenient().when(commentRepo.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ContentItem published() {
        ContentItem c = new ContentItem();
        c.setId(7L);
        c.setState(ContentItem.ContentState.PUBLISHED);
        return c;
    }

    // ── createComment ────────────────────────────────────────────────────

    @Test
    void createComment_withZeroFilterHits_landsInPending() {
        when(contentRepo.findPublishedById(eq(7L), any(Instant.class)))
                .thenReturn(Optional.of(published()));
        when(filterService.countSensitiveWordHits("hello world")).thenReturn(0);

        Comment saved = svc.createComment(7L, null, "hello world", 42L);

        assertThat(saved.getModerationState()).isEqualTo(ModerationState.PENDING);
        assertThat(saved.getFilterHitCount()).isZero();
        assertThat(saved.getAuthorId()).isEqualTo(42L);
        assertThat(saved.getContentItemId()).isEqualTo(7L);
    }

    @Test
    void createComment_withTwoOrMoreFilterHits_landsInHoldForReview() {
        when(contentRepo.findPublishedById(eq(7L), any(Instant.class)))
                .thenReturn(Optional.of(published()));
        when(filterService.countSensitiveWordHits("bad evil text")).thenReturn(2);

        Comment saved = svc.createComment(7L, null, "bad evil text", 42L);

        assertThat(saved.getModerationState()).isEqualTo(ModerationState.HOLD_FOR_REVIEW);
        assertThat(saved.getFilterHitCount()).isEqualTo(2);
    }

    @Test
    void createComment_withSingleHit_isStillPending_threshold_isExclusiveBelowTwo() {
        when(contentRepo.findPublishedById(eq(7L), any(Instant.class)))
                .thenReturn(Optional.of(published()));
        when(filterService.countSensitiveWordHits(anyString())).thenReturn(1);

        Comment saved = svc.createComment(7L, null, "one bad word", 42L);

        assertThat(saved.getModerationState()).isEqualTo(ModerationState.PENDING);
        assertThat(saved.getFilterHitCount()).isEqualTo(1);
    }

    @Test
    void createComment_onUnpublishedContent_throws404BusinessException() {
        when(contentRepo.findPublishedById(eq(7L), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.createComment(7L, null, "anything", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("published content");
    }

    // ── performAction ────────────────────────────────────────────────────

    private Comment pendingComment(long id) {
        Comment c = new Comment();
        c.setId(id);
        c.setContentItemId(7L);
        c.setAuthorId(42L);
        c.setBody("text");
        c.setModerationState(ModerationState.PENDING);
        return c;
    }

    @Test
    void performAction_approve_setsApproved_andPersistsAction_andAuditsTransition() {
        Comment c = pendingComment(11L);
        when(commentRepo.findById(11L)).thenReturn(Optional.of(c));
        when(actionRepo.save(any(ModerationAction.class))).thenAnswer(i -> i.getArgument(0));

        ModerationAction action = svc.performAction(11L, "APPROVE", "Looks good", 99L);

        assertThat(c.getModerationState()).isEqualTo(ModerationState.APPROVED);
        assertThat(action.getCommentId()).isEqualTo(11L);
        assertThat(action.getAction()).isEqualTo("APPROVE");
        assertThat(action.getActorId()).isEqualTo(99L);
        assertThat(action.getReason()).isEqualTo("Looks good");

        verify(commentRepo).save(c);
        verify(auditService).log(eq(99L), eq("MODERATOR"), eq("MODERATION_APPROVE"),
                eq("comment"), eq("11"), eq("state:PENDING->APPROVED"));
    }

    @Test
    void performAction_reject_setsRejected() {
        Comment c = pendingComment(12L);
        when(commentRepo.findById(12L)).thenReturn(Optional.of(c));
        when(actionRepo.save(any(ModerationAction.class))).thenAnswer(i -> i.getArgument(0));

        svc.performAction(12L, "REJECT", "violates policy", 99L);

        assertThat(c.getModerationState()).isEqualTo(ModerationState.REJECTED);
    }

    @Test
    void performAction_escalate_setsEscalated() {
        Comment c = pendingComment(13L);
        when(commentRepo.findById(13L)).thenReturn(Optional.of(c));
        when(actionRepo.save(any(ModerationAction.class))).thenAnswer(i -> i.getArgument(0));

        svc.performAction(13L, "ESCALATE", "needs admin review", 99L);

        assertThat(c.getModerationState()).isEqualTo(ModerationState.ESCALATED);
    }

    @Test
    void performAction_unknownAction_throws400BusinessException() {
        when(commentRepo.findById(14L)).thenReturn(Optional.of(pendingComment(14L)));

        assertThatThrownBy(() -> svc.performAction(14L, "PURGE", "nope", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid action");
    }

    @Test
    void performAction_unknownComment_throws404BusinessException() {
        when(commentRepo.findById(15L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.performAction(15L, "APPROVE", "x", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Comment not found");
    }

    // ── sensitive word lifecycle ─────────────────────────────────────────

    @Test
    void addWord_persistsAndAudits() {
        ArgumentCaptor<SensitiveWord> cap = ArgumentCaptor.forClass(SensitiveWord.class);
        when(wordRepo.save(any(SensitiveWord.class))).thenAnswer(i -> {
            SensitiveWord sw = i.getArgument(0);
            sw.setId(123L);
            return sw;
        });

        SensitiveWord saved = svc.addWord("badword", 99L);

        verify(wordRepo).save(cap.capture());
        assertThat(cap.getValue().getWord()).isEqualTo("badword");
        assertThat(saved.getId()).isEqualTo(123L);
        verify(auditService).log(eq(99L), eq("MODERATOR"), eq("SENSITIVE_WORD_ADD"),
                eq("sensitive_word"), eq("123"), eq(null));
    }

    @Test
    void updateWord_appliesPartialPatch_andAudits() {
        SensitiveWord existing = new SensitiveWord();
        existing.setId(50L);
        existing.setWord("old");
        existing.setActive(true);
        when(wordRepo.findById(50L)).thenReturn(Optional.of(existing));
        when(wordRepo.save(any(SensitiveWord.class))).thenAnswer(i -> i.getArgument(0));

        // Patch both fields.
        SensitiveWord updated = svc.updateWord(50L, "new", false, 99L);

        assertThat(updated.getWord()).isEqualTo("new");
        assertThat(updated.isActive()).isFalse();
        verify(auditService).log(eq(99L), eq("MODERATOR"), eq("SENSITIVE_WORD_UPDATE"),
                eq("sensitive_word"), eq("50"), eq(null));
    }

    @Test
    void updateWord_nullArgs_leaveExistingValuesUntouched() {
        SensitiveWord existing = new SensitiveWord();
        existing.setId(51L);
        existing.setWord("keep");
        existing.setActive(true);
        when(wordRepo.findById(51L)).thenReturn(Optional.of(existing));
        when(wordRepo.save(any(SensitiveWord.class))).thenAnswer(i -> i.getArgument(0));

        SensitiveWord updated = svc.updateWord(51L, null, null, 99L);

        // Null patches must NOT clobber previously-set values.
        assertThat(updated.getWord()).isEqualTo("keep");
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void updateWord_unknownId_throws404BusinessException() {
        when(wordRepo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.updateWord(404L, "x", true, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sensitive word not found");
    }

    @Test
    void deleteWord_callsRepoDeleteAndAudits() {
        svc.deleteWord(60L, 99L);

        verify(wordRepo).deleteById(60L);
        verify(auditService).log(eq(99L), eq("MODERATOR"), eq("SENSITIVE_WORD_DELETE"),
                eq("sensitive_word"), eq("60"), eq(null));
    }
}
