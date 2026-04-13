package com.civicworks.billing.infra;

import com.civicworks.billing.domain.FeeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeItemRepository extends JpaRepository<FeeItem, Long> {
    List<FeeItem> findByActiveTrue();
    boolean existsByCode(String code);
}
