package com.civicworks.billing.infra;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.Bill.BillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    Page<Bill> findAll(Pageable pageable);

    // Grace period is strict: late fees apply only AFTER the full 10-day grace,
    // i.e. starting day 11. Callers pass cutoffDate = today - GRACE_PERIOD_DAYS;
    // using `<` (not `<=`) ensures a bill with dueDate == today - 10 is NOT
    // eligible (day 10), while dueDate < today - 10 IS eligible (day 11+).
    @Query("SELECT b FROM Bill b WHERE b.status IN ('OPEN', 'OVERDUE') " +
            "AND b.dueDate < :cutoffDate AND b.balance > 0")
    List<Bill> findEligibleForLateFee(@Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT b FROM Bill b WHERE b.accountId = :accountId")
    List<Bill> findByAccountId(@Param("accountId") Long accountId);
}
