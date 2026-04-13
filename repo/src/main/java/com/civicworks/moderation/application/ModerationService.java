package com.civicworks.moderation.application;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.content.infra.ContentItemRepository;
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
import com.civicworks.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModerationService {

    private final CommentRepository commentRepo;
    private final ModerationActionRepository actionRepo;
    private final SensitiveWordRepository wordRepo;
    private final ContentItemRepository contentRepo;
    private final CommentFilterService filterService;
    private final AuditService auditService;
    private final MunicipalClock clock;

    public ModerationService(CommentRepository commentRepo,
                             ModerationActionRepository actionRepo,
                             SensitiveWordRepository wordRepo,
                             ContentItemRepository contentRepo,
                             CommentFilterService filterService,
                             AuditService auditService,
                             MunicipalClock clock) {
        this.commentRepo = commentRepo;
        this.actionRepo = actionRepo;
        this.wordRepo = wordRepo;
        this.contentRepo = contentRepo;
        this.filterService = filterService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Comment createComment(Long contentItemId, Long parentId, String body, Long authorId) {
        // Validate: comments only on published content
        ContentItem content = contentRepo.findPublishedById(contentItemId, clock.instant())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_PUBLIC,
                        "Comments can only be created on published content",
                        org.springframework.http.HttpStatus.NOT_FOUND));

        int hits = filterService.countSensitiveWordHits(body);

        Comment comment = new Comment();
        comment.setContentItemId(contentItemId);
        comment.setParentId(parentId);
        comment.setAuthorId(authorId);
        comment.setBody(body);
        comment.setFilterHitCount(hits);
        comment.setModerationState(hits >= 2 ? ModerationState.HOLD_FOR_REVIEW : ModerationState.PENDING);

        return commentRepo.save(comment);
    }

    @Transactional(readOnly = true)
    public Page<Comment> listCommentsByState(ModerationState state, int page, int size) {
        return commentRepo.findByModerationState(state, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional
    public ModerationAction performAction(Long commentId, String action, String reason, Long actorId) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("Comment not found"));

        ModerationState newState = switch (action) {
            case "APPROVE" -> ModerationState.APPROVED;
            case "REJECT" -> ModerationState.REJECTED;
            case "ESCALATE" -> ModerationState.ESCALATED;
            default -> throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR,
                    "Invalid action: " + action);
        };

        String before = comment.getModerationState().name();
        comment.setModerationState(newState);
        commentRepo.save(comment);

        ModerationAction ma = new ModerationAction();
        ma.setCommentId(commentId);
        ma.setAction(action);
        ma.setActorId(actorId);
        ma.setReason(reason);
        ModerationAction saved = actionRepo.save(ma);

        auditService.log(actorId, "MODERATOR", "MODERATION_" + action, "comment",
                commentId.toString(), "state:" + before + "->" + newState);
        return saved;
    }

    // Sensitive word CRUD
    @Transactional
    public SensitiveWord addWord(String word, Long actorId) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word);
        SensitiveWord saved = wordRepo.save(sw);
        auditService.log(actorId, "MODERATOR", "SENSITIVE_WORD_ADD", "sensitive_word",
                saved.getId().toString(), null);
        return saved;
    }

    @Transactional
    public SensitiveWord updateWord(Long id, String word, Boolean active, Long actorId) {
        SensitiveWord sw = wordRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Sensitive word not found"));
        if (word != null) sw.setWord(word);
        if (active != null) sw.setActive(active);
        SensitiveWord saved = wordRepo.save(sw);
        auditService.log(actorId, "MODERATOR", "SENSITIVE_WORD_UPDATE", "sensitive_word",
                id.toString(), null);
        return saved;
    }

    @Transactional
    public void deleteWord(Long id, Long actorId) {
        wordRepo.deleteById(id);
        auditService.log(actorId, "MODERATOR", "SENSITIVE_WORD_DELETE", "sensitive_word",
                id.toString(), null);
    }

    public List<SensitiveWord> listWords() {
        return wordRepo.findAll();
    }
}
