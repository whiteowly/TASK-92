package com.civicworks.moderation.infra;

import com.civicworks.moderation.domain.ModerationAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
}
