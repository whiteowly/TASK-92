package com.civicworks.notifications.infra;

import com.civicworks.notifications.domain.TaskReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface TaskReminderRepository extends JpaRepository<TaskReminder, Long> {

    default List<TaskReminder> findDueReminders(Instant now) {
        return findAll().stream()
                .filter(r -> !r.isSent() && r.getScheduledAt().isBefore(now)
                        && r.getRetryCount() < r.getMaxRetries())
                .toList();
    }
}
