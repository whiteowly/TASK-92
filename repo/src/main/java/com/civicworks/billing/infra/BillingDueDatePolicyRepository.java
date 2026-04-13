package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillingDueDatePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BillingDueDatePolicyRepository extends JpaRepository<BillingDueDatePolicy, Long> {

    @Query("SELECT p FROM BillingDueDatePolicy p WHERE p.cycleType = :cycleType " +
            "AND p.effectiveFrom <= :asOf ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<BillingDueDatePolicy> findActivePolicy(
            @Param("cycleType") String cycleType,
            @Param("asOf") Instant asOf);

    @Query("SELECT p FROM BillingDueDatePolicy p WHERE p.cycleType = :cycleType " +
            "ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<BillingDueDatePolicy> findLatestPolicy(@Param("cycleType") String cycleType);
}
