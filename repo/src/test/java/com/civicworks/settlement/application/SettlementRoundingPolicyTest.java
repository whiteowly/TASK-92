package com.civicworks.settlement.application;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.BillLineItem;
import com.civicworks.billing.infra.BillLineItemRepository;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.settlement.domain.Payment;
import com.civicworks.settlement.domain.PaymentAllocation;
import com.civicworks.settlement.infra.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Nearest-cent rounding policy for proportional split, with deterministic
 * residual allocation on the last line so the allocations reconcile
 * exactly to the payment amount.
 */
@ExtendWith(MockitoExtension.class)
class SettlementRoundingPolicyTest {

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
    }

    private static BillLineItem line(long id, String amount) {
        BillLineItem li = new BillLineItem();
        li.setId(id);
        li.setAmount(new BigDecimal(amount));
        return li;
    }

    private List<PaymentAllocation> capture(Bill bill, BigDecimal pay, List<BillLineItem> lines) {
        Payment p = new Payment();
        p.setId(42L); p.setBillId(bill.getId()); p.setAmount(pay);
        ArgumentCaptor<PaymentAllocation> cap = ArgumentCaptor.forClass(PaymentAllocation.class);
        when(allocationRepo.save(any(PaymentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        svc.allocateProportional(p, lines, bill);
        verify(allocationRepo, times(lines.size())).save(cap.capture());
        return cap.getAllValues();
    }

    @Test
    void proportionalUsesHalfUp_notDown_onIntermediateLines() {
        // Two equal lines; payment $0.01 → weight 0.5 * 0.01 = 0.005.
        // HALF_UP → $0.01 on line 1; last absorbs -> $0.00. DOWN would give $0.
        Bill bill = new Bill();
        bill.setId(1L);
        bill.setOriginalAmount(new BigDecimal("20.00"));
        bill.setDiscountAmount(BigDecimal.ZERO);
        List<BillLineItem> lines = List.of(line(11L, "10.00"), line(12L, "10.00"));

        List<PaymentAllocation> allocs = capture(bill, new BigDecimal("0.01"), lines);
        assertEquals(new BigDecimal("0.01"), allocs.get(0).getAllocatedAmount(),
                "HALF_UP must round 0.005 up to 0.01 on the first line");
        // Last-line residual reconciles
        BigDecimal sum = allocs.stream().map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("0.01").compareTo(sum));
    }

    @Test
    void sumReconcilesExactly_underNearestCentRounding() {
        Bill bill = new Bill();
        bill.setId(2L);
        bill.setOriginalAmount(new BigDecimal("100.00"));
        bill.setDiscountAmount(BigDecimal.ZERO);
        List<BillLineItem> lines = List.of(
                line(21L, "33.33"), line(22L, "33.34"), line(23L, "33.33"));
        BigDecimal pay = new BigDecimal("10.00");

        List<PaymentAllocation> allocs = capture(bill, pay, lines);
        BigDecimal sum = allocs.stream().map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, pay.compareTo(sum),
                "nearest-cent rounding must still reconcile exactly; got " + sum);
    }

    @Test
    void deterministic_residualAbsorbedOnLastLine() {
        // Same inputs → same allocations every run.
        Bill bill = new Bill();
        bill.setId(3L);
        bill.setOriginalAmount(new BigDecimal("100.00"));
        bill.setDiscountAmount(BigDecimal.ZERO);
        List<BillLineItem> lines = List.of(
                line(31L, "10.00"), line(32L, "20.00"), line(33L, "70.00"));
        BigDecimal pay = new BigDecimal("7.77");

        List<PaymentAllocation> a1 = capture(bill, pay, lines);
        reset(allocationRepo);
        List<PaymentAllocation> a2 = capture(bill, pay, lines);

        for (int i = 0; i < a1.size(); i++) {
            assertEquals(0, a1.get(i).getAllocatedAmount()
                    .compareTo(a2.get(i).getAllocatedAmount()),
                    "allocation on line " + i + " must be deterministic");
        }
    }
}
