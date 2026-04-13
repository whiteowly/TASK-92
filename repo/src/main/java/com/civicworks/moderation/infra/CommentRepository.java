package com.civicworks.moderation.infra;

import com.civicworks.moderation.domain.Comment;
import com.civicworks.moderation.domain.Comment.ModerationState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByContentItemIdOrderByCreatedAtAsc(Long contentItemId);
    Page<Comment> findByModerationState(ModerationState state, Pageable pageable);
}
