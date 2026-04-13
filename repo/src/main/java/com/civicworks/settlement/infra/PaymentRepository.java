package com.civicworks.settlement.infra;

import com.civicworks.settlement.domain.Payment;
import com.civicworks.settlement.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillIdAndReversedFalse(Long billId);

    /**
     * Net (non-reversed) total per method for legacy/untagged payments
     * matched by operator + time window. Excludes any payment already tagged
     * with a shift_id to avoid double-counting alongside the shift_id query.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.postedBy = :userId " +
            "AND p.shiftId IS NULL AND p.method = :method AND p.reversed = false " +
            "AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumByMethodForShift(@Param("userId") Long userId, @Param("method") PaymentMethod method,
                                    @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Gross posted A/R total for legacy/untagged payments for the shift
     * window. Mirrors {@link #sumByMethodForShift}'s untagged scope.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.postedBy = :userId " +
            "AND p.shiftId IS NULL AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumPostedArForShift(@Param("userId") Long userId,
                                    @Param("from") Instant from, @Param("to") Instant to);

    /** Net (non-reversed) total per method scoped strictly by shift_id. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.shiftId = :shiftId " +
            "AND p.method = :method AND p.reversed = false")
    BigDecimal sumByMethodForShiftId(@Param("shiftId") Long shiftId,
                                      @Param("method") PaymentMethod method);

    /** Gross (incl. reversed) total scoped strictly by shift_id. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.shiftId = :shiftId")
    BigDecimal sumPostedArForShiftId(@Param("shiftId") Long shiftId);
}
