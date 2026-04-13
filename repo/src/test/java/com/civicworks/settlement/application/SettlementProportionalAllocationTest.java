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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B) Proportional allocation must remain valid in the presence of discounts:
 * every line allocation must be >= 0 and the sum of allocations must equal
 * the payment amount exactly. The previous implementation divided
 * undiscounted line amounts by the discounted bill total, which produced
 * weights summing to > 1 and could push the last line negative.
 */
@ExtendWith(MockitoExtension.class)
class SettlementProportionalAllocationTest {

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

    private List<PaymentAllocation> runAndCapture(Bill bill, BigDecimal payAmount,
                                                   List<BillLineItem> lines) {
        Payment p = new Payment();
        p.setId(123L);
        p.setBillId(bill.getId());
        p.setAmount(payAmount);

        ArgumentCaptor<PaymentAllocation> cap = ArgumentCaptor.forClass(PaymentAllocation.class);
        when(allocationRepo.save(any(PaymentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        svc.allocateProportional(p, lines, bill);

        verify(allocationRepo, times(lines.size())).save(cap.capture());
        return cap.getAllValues();
    }

    @Test
    void heavyDiscount_payFullDiscountedBalance_allLinesNonNegative_sumExact() {
        // Original $100 bill, $40 discount → $60 net payable. Pay the $60 in full.
        Bill bill = new Bill();
        bill.setId(1L);
        bill.setOriginalAmount(new BigDecimal("100.00"));
        bill.setDiscountAmount(new BigDecimal("40.00"));

        List<BillLineItem> lines = List.of(
                line(11L, "30.00"),
                line(12L, "30.00"),
                line(13L, "40.00")
        );
        BigDecimal pay = new BigDecimal("60.00");

        List<PaymentAllocation> allocs = runAndCapture(bill, pay, lines);

        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentAllocation a : allocs) {
            assertTrue(a.getAllocatedAmount().signum() >= 0,
                    "negative allocation on line " + a.getLineItemId());
            sum = sum.add(a.getAllocatedAmount());
        }
        assertEquals(0, pay.compareTo(sum),
                "allocations must sum to payment amount exactly: sum=" + sum);
    }

    @Test
    void noDiscount_partialPayment_sumsExactly() {
        Bill bill = new Bill();
        bill.setId(2L);
        bill.setOriginalAmount(new BigDecimal("100.00"));
        bill.setDiscountAmount(BigDecimal.ZERO);

        List<BillLineItem> lines = List.of(
                line(21L, "33.33"),
                line(22L, "33.34"),
                line(23L, "33.33")
        );
        BigDecimal pay = new BigDecimal("50.00");

        List<PaymentAllocation> allocs = runAndCapture(bill, pay, lines);
        BigDecimal sum = allocs.stream().map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, pay.compareTo(sum));
        for (PaymentAllocation a : allocs) {
            assertTrue(a.getAllocatedAmount().signum() >= 0);
        }
    }

    @Test
    void roundingResidual_lastLineAbsorbsDownToZero_neverNegative() {
        // Pathological: tiny payment vs many lines. Forces multiple
        // micro-allocations that round up cumulatively; the last-line
        // residual must clamp at 0 rather than go negative.
        Bill bill = new Bill();
        bill.setId(3L);
        bill.setOriginalAmount(new BigDecimal("100.00"));
        bill.setDiscountAmount(BigDecimal.ZERO);

        List<BillLineItem> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) lines.add(line(30L + i, "10.00"));
        BigDecimal pay = new BigDecimal("0.05"); // 5 cents across 10 lines

        List<PaymentAllocation> allocs = runAndCapture(bill, pay, lines);
        BigDecimal sum = allocs.stream().map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, pay.compareTo(sum));
        for (PaymentAllocation a : allocs) {
            assertTrue(a.getAllocatedAmount().signum() >= 0,
                    "negative allocation on line " + a.getLineItemId() + " amount=" + a.getAllocatedAmount());
        }
    }

    @Test
    void zeroOriginal_returnsWithoutAllocations() {
        Bill bill = new Bill();
        bill.setId(4L);
        bill.setOriginalAmount(BigDecimal.ZERO);

        Payment p = new Payment();
        p.setId(99L); p.setAmount(new BigDecimal("10.00"));
        svc.allocateProportional(p, List.of(line(41L, "0.00")), bill);

        verify(allocationRepo, never()).save(any());
    }
}
