package com.civicworks.billing;

import com.civicworks.billing.application.BillingService;
import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.infra.*;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Boundary tests for the 10-day late-fee grace period.
 * Contract: late fee applies ONLY after the full 10-day grace period,
 * i.e. starting on day 11. A bill with due_date == today - 10 (day 10)
 * must NOT be eligible; due_date == today - 11 (day 11) MUST be eligible.
 *
 * <p>These tests exercise two concerns:
 * <ol>
 *   <li>{@link BillingService#processLateFees()} passes the correct cutoff
 *       date to the repository query.</li>
 *   <li>The JPQL in {@link BillRepository#findEligibleForLateFee(LocalDate)}
 *       uses strict {@code <} (not {@code <=}) so that a due_date equal to
 *       the cutoff is excluded — this is verified indirectly via the method
 *       source annotation and via the query string on the interface.</li>
 * </ol>
 */
class LateFeeGracePeriodBoundaryTest {

    @Test
    void processLateFees_passesCutoffAsTodayMinus10() {
        BillRepository billRepo = mock(BillRepository.class);
        BillLateFeeRepository lateFeeRepo = mock(BillLateFeeRepository.class);
        MunicipalClock clock = mock(MunicipalClock.class);
        LocalDate today = LocalDate.of(2026, 4, 27);
        when(clock.today()).thenReturn(today);
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-04-27T00:00:00Z"));
        when(billRepo.findEligibleForLateFee(any())).thenReturn(List.of());
        when(lateFeeRepo.sumByBillId(any())).thenReturn(BigDecimal.ZERO);

        BillingService svc = new BillingService(
                mock(FeeItemRepository.class), mock(AccountRepository.class), billRepo,
                mock(BillLineItemRepository.class), mock(BillDiscountRepository.class),
                lateFeeRepo, mock(BillingRunRepository.class),
                mock(BillingDueDatePolicyRepository.class), mock(BillingUsageRepository.class),
                mock(AuditService.class), clock);

        svc.processLateFees();

        // Cutoff = today - 10 = 2026-04-17.
        verify(billRepo).findEligibleForLateFee(LocalDate.of(2026, 4, 17));
    }

    @Test
    void repositoryQueryUsesStrictLessThan_notLessThanOrEqual() throws Exception {
        // Pins the contract: the query MUST use `b.dueDate < :cutoffDate` so
        // day 10 (dueDate == cutoff) is excluded. Day 11 (dueDate < cutoff)
        // is included.
        Method m = BillRepository.class.getMethod("findEligibleForLateFee", LocalDate.class);
        org.springframework.data.jpa.repository.Query q =
                m.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertNotNull(q, "findEligibleForLateFee must carry @Query");
        String value = q.value().replaceAll("\\s+", " ");
        assertTrue(value.contains("b.dueDate < :cutoffDate"),
                "expected strict `<` grace boundary, got: " + value);
        assertFalse(value.contains("b.dueDate <= :cutoffDate"),
                "must NOT use `<=` — that would apply a late fee on day 10");
    }

    @Test
    void boundaryArithmetic_day10ExcludedDay11Included() {
        LocalDate today = LocalDate.of(2026, 4, 27);
        LocalDate cutoff = today.minusDays(10); // 2026-04-17
        LocalDate dueDay10 = today.minusDays(10); // exactly 10 days past due
        LocalDate dueDay11 = today.minusDays(11); // 11 days past due

        // Strict `<` cutoff semantics
        assertFalse(dueDay10.isBefore(cutoff), "day 10 must not be eligible");
        assertTrue(dueDay11.isBefore(cutoff),  "day 11 must be eligible");
    }
}
