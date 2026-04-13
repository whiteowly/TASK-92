package com.civicworks.notifications.infra;

import com.civicworks.notifications.domain.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findByExportedFalse();
}
