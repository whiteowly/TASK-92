package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillingUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BillingUsageRepository extends JpaRepository<BillingUsage, Long> {

    /** Latest usage record at or before the cycle date for an account/fee. */
    @Query("SELECT u FROM BillingUsage u WHERE u.accountId = :accountId " +
            "AND u.feeItemId = :feeItemId AND u.cycleDate <= :cycleDate " +
            "ORDER BY u.cycleDate DESC, u.id DESC LIMIT 1")
    Optional<BillingUsage> findLatestUpTo(@Param("accountId") Long accountId,
                                           @Param("feeItemId") Long feeItemId,
                                           @Param("cycleDate") LocalDate cycleDate);
}
