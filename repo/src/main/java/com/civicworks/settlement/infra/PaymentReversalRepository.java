package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.PaymentReversal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentReversalRepository extends JpaRepository<PaymentReversal, Long> {
    Optional<PaymentReversal> findByOriginalPaymentId(Long originalPaymentId);
}
