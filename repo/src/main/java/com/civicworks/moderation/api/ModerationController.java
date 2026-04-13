package com.civicworks.moderation.api;

import com.civicworks.moderation.application.ModerationService;
import com.civicworks.moderation.domain.Comment;
import com.civicworks.moderation.domain.Comment.ModerationState;
import com.civicworks.moderation.domain.ModerationAction;
import com.civicworks.moderation.domain.SensitiveWord;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/content-items/{contentItemId}/comments")
    public ResponseEntity<Comment> createComment(
            @PathVariable Long contentItemId,
            @Valid @RequestBody CreateCommentRequest request) {
        Comment comment = moderationService.createComment(
                contentItemId, request.parentId(), request.body(), SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping("/moderation/comments")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Page<Comment>> listComments(
            @RequestParam(required = false) ModerationState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(moderationService.listCommentsByState(
                state != null ? state : ModerationState.PENDING, page, size));
    }

    @PostMapping("/moderation/comments/{commentId}/actions")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ModerationAction> performAction(
            @PathVariable Long commentId,
            @Valid @RequestBody ModerationActionRequest request) {
        return ResponseEntity.ok(moderationService.performAction(
                commentId, request.action(), request.reason(), SecurityUtils.currentUserId()));
    }

    @PostMapping("/moderation/sensitive-words")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<SensitiveWord> addWord(@Valid @RequestBody SensitiveWordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moderationService.addWord(request.word(), SecurityUtils.currentUserId()));
    }

    @PutMapping("/moderation/sensitive-words/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<SensitiveWord> updateWord(@PathVariable Long id,
                                                     @RequestBody SensitiveWordRequest request) {
        return ResponseEntity.ok(moderationService.updateWord(
                id, request.word(), request.active(), SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/moderation/sensitive-words/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Void> deleteWord(@PathVariable Long id) {
        moderationService.deleteWord(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderation/sensitive-words")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<List<SensitiveWord>> listWords() {
        return ResponseEntity.ok(moderationService.listWords());
    }

    public record CreateCommentRequest(@NotBlank String body, Long parentId) {}
    public record ModerationActionRequest(@NotBlank String action, @NotBlank String reason) {}
    public record SensitiveWordRequest(@NotBlank String word, Boolean active) {}
}
