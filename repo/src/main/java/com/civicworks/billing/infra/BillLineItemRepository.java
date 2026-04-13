package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillLineItemRepository extends JpaRepository<BillLineItem, Long> {
    List<BillLineItem> findByBillIdOrderByLineOrderAsc(Long billId);
}
