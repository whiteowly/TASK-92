package com.civicworks.notifications.infra;

import com.civicworks.notifications.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
}
