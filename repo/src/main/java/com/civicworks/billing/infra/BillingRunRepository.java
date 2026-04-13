package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillingRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRunRepository extends JpaRepository<BillingRun, Long> {
}
