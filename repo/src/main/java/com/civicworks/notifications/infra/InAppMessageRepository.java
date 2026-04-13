package com.civicworks.notifications.infra;

import com.civicworks.notifications.domain.InAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InAppMessageRepository extends JpaRepository<InAppMessage, Long> {
    List<InAppMessage> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}
