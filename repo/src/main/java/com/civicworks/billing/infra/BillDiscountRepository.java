package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillDiscountRepository extends JpaRepository<BillDiscount, Long> {
}
