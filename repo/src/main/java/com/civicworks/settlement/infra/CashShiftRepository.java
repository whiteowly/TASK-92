package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.CashShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    @Query("SELECT s FROM CashShift s WHERE s.operatorId = :operatorId AND s.status = 'OPEN' " +
            "ORDER BY s.startedAt DESC LIMIT 1")
    Optional<CashShift> findOpenShiftForOperator(@Param("operatorId") Long operatorId);
}
