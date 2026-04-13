package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
}
