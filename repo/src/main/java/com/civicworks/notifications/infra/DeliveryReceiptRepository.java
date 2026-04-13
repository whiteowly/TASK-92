package com.civicworks.notifications.infra;

import com.civicworks.notifications.domain.DeliveryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, Long> {
}
