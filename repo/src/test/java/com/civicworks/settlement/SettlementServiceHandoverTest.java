package com.civicworks.settlement;

import com.civicworks.billing.infra.BillLineItemRepository;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.settlement.application.SettlementService;
import com.civicworks.settlement.domain.*;
import com.civicworks.settlement.infra.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A) Verifies the discrepancy workflow now uses a real posted-A/R basis
 * (sum of every payment posted in the shift, including reversed ones)
 * compared against the net cash totals (excluding reversed). Variance
 * over $1.00 must create a discrepancy case; variance &lt;= $1.00 must not.
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceHandoverTest {

    @Mock private PaymentRepository paymentRepo;
    @Mock private PaymentAllocationRepository allocationRepo;
    @Mock private PaymentReversalRepository reversalRepo;
    @Mock private CashShiftRepository shiftRepo;
    @Mock private ShiftHandoverReportRepository handoverRepo;
    @Mock private DiscrepancyCaseRepository discrepancyRepo;
    @Mock private BillRepository billRepo;
    @Mock private BillLineItemRepository lineItemRepo;
    @Mock private AuditService auditService;

    private SettlementService svc;

    @BeforeEach
    void setUp() {
        svc = new SettlementService(paymentRepo, allocationRepo, reversalRepo, shiftRepo,
                handoverRepo, discrepancyRepo, billRepo, lineItemRepo, auditService);

        CashShift shift = new CashShift();
        shift.setId(7L);
        shift.setOperatorId(99L);
        shift.setStartedAt(Instant.parse("2026-04-13T08:00:00Z"));
        shift.setEndedAt(Instant.parse("2026-04-13T16:00:00Z"));
        shift.setStatus("OPEN");
        when(shiftRepo.findById(7L)).thenReturn(Optional.of(shift));

        when(handoverRepo.save(any(ShiftHandoverReport.class)))
                .thenAnswer(inv -> {
                    ShiftHandoverReport r = inv.getArgument(0);
                    r.setShiftId(7L);
                    return r;
                });
    }

    private void stubMethodTotals(BigDecimal cash, BigDecimal check, BigDecimal voucher,
                                   BigDecimal other, BigDecimal grossPostedAr) {
        // shift_id-scoped queries return zero in this test (no payments
        // tagged with shift_id), forcing the legacy operator+window queries
        // to provide the totals being asserted.
        when(paymentRepo.sumByMethodForShiftId(eq(7L), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepo.sumPostedArForShiftId(eq(7L))).thenReturn(BigDecimal.ZERO);

        when(paymentRepo.sumByMethodForShift(eq(99L), eq(PaymentMethod.CASH), any(), any()))
                .thenReturn(cash);
        when(paymentRepo.sumByMethodForShift(eq(99L), eq(PaymentMethod.CHECK), any(), any()))
                .thenReturn(check);
        when(paymentRepo.sumByMethodForShift(eq(99L), eq(PaymentMethod.VOUCHER), any(), any()))
                .thenReturn(voucher);
        when(paymentRepo.sumByMethodForShift(eq(99L), eq(PaymentMethod.OTHER), any(), any()))
                .thenReturn(other);
        when(paymentRepo.sumPostedArForShift(eq(99L), any(), any()))
                .thenReturn(grossPostedAr);
    }

    @Test
    void discrepancyCreated_whenVarianceExceedsOneDollar() {
        // Net cash $100, gross posted A/R $105 (i.e. $5 was reversed during shift).
        stubMethodTotals(new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("105.00"));

        ShiftHandoverReport report = svc.handover(7L, 1L);

        assertEquals(0, new BigDecimal("5.00").compareTo(report.getVariance()));
        assertEquals(0, new BigDecimal("105.00").compareTo(report.getPostedArTotal()));

        ArgumentCaptor<DiscrepancyCase> cap = ArgumentCaptor.forClass(DiscrepancyCase.class);
        verify(discrepancyRepo, times(1)).save(cap.capture());
        assertEquals(0, new BigDecimal("5.00").compareTo(cap.getValue().getVarianceAmount()));
    }

    @Test
    void noDiscrepancy_whenVarianceWithinThreshold() {
        // Net cash $100, gross posted A/R $100.50 (50 cents reversed).
        stubMethodTotals(new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("100.50"));

        ShiftHandoverReport report = svc.handover(7L, 1L);

        assertEquals(0, new BigDecimal("0.50").compareTo(report.getVariance()));
        verify(discrepancyRepo, never()).save(any(DiscrepancyCase.class));
    }

    @Test
    void noDiscrepancy_whenPaymentsAndPostedArMatchExactly() {
        stubMethodTotals(new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("100.00"));

        ShiftHandoverReport report = svc.handover(7L, 1L);

        assertEquals(0, BigDecimal.ZERO.compareTo(report.getVariance()));
        verify(discrepancyRepo, never()).save(any(DiscrepancyCase.class));
    }

    @Test
    void postedArTotalIsNotMirroredFromPaymentTotal() {
        // Regression guard: the bug being fixed had postedAr := paymentTotal,
        // making variance always zero. Provide different values and confirm
        // the report stores the gross A/R value, not the cash total.
        stubMethodTotals(new BigDecimal("250.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("260.00"));

        ShiftHandoverReport report = svc.handover(7L, 1L);

        assertEquals(0, new BigDecimal("260.00").compareTo(report.getPostedArTotal()));
        assertNotEquals(report.getCashTotal(), report.getPostedArTotal());
    }
}
