package com.civicworks.billing.infra;

import com.civicworks.billing.domain.BillLateFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface BillLateFeeRepository extends JpaRepository<BillLateFee, Long> {
    @Query("SELECT COALESCE(SUM(f.feeAmount), 0) FROM BillLateFee f WHERE f.billId = :billId")
    BigDecimal sumByBillId(@Param("billId") Long billId);
}
