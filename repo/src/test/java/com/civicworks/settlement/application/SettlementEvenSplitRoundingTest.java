package com.civicworks.settlement.application;

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
 * Even-split allocation must:
 *  - follow the nearest-$0.01 rounding policy;
 *  - reconcile exactly to the payment amount;
 *  - produce non-negative, deterministic allocations.
 * The previous implementation used integer-cent math but paired with
 * RoundingMode.DOWN on the initial even-part division, which was fine for
 * integer cents but did not share a single policy with proportional split.
 */
@ExtendWith(MockitoExtension.class)
class SettlementEvenSplitRoundingTest {

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

    private static BillLineItem line(long id) {
        BillLineItem li = new BillLineItem();
        li.setId(id);
        li.setAmount(new BigDecimal("10.00"));
        return li;
    }

    private List<PaymentAllocation> run(BigDecimal pay, int lineCount) {
        Payment p = new Payment();
        p.setId(1L); p.setAmount(pay);
        List<BillLineItem> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) lines.add(line(10L + i));

        ArgumentCaptor<PaymentAllocation> cap = ArgumentCaptor.forClass(PaymentAllocation.class);
        when(allocationRepo.save(any(PaymentAllocation.class))).thenAnswer(i -> i.getArgument(0));
        svc.allocateEvenSplit(p, lines);
        verify(allocationRepo, times(lineCount)).save(cap.capture());
        return cap.getAllValues();
    }

    private void assertInvariants(BigDecimal pay, List<PaymentAllocation> allocs) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentAllocation a : allocs) {
            assertTrue(a.getAllocatedAmount().signum() >= 0,
                    "negative allocation not allowed: " + a.getAllocatedAmount());
            sum = sum.add(a.getAllocatedAmount());
        }
        assertEquals(0, pay.compareTo(sum), "allocations must sum to payment: " + sum);
    }

    @Test
    void exactEvenSplit_noResidual() {
        List<PaymentAllocation> a = run(new BigDecimal("100.00"), 4);
        for (PaymentAllocation x : a) {
            assertEquals(0, new BigDecimal("25.00").compareTo(x.getAllocatedAmount()));
        }
        assertInvariants(new BigDecimal("100.00"), a);
    }

    @Test
    void oddCentDistribution_residualGoesToEarliestLines() {
        // $1.00 / 3 = 0.3333... → 34¢, 33¢, 33¢ with 1¢ residual on line 0.
        List<PaymentAllocation> a = run(new BigDecimal("1.00"), 3);
        assertEquals(0, new BigDecimal("0.34").compareTo(a.get(0).getAllocatedAmount()));
        assertEquals(0, new BigDecimal("0.33").compareTo(a.get(1).getAllocatedAmount()));
        assertEquals(0, new BigDecimal("0.33").compareTo(a.get(2).getAllocatedAmount()));
        assertEquals(1, a.get(0).getResidualCents());
        assertEquals(0, a.get(1).getResidualCents());
        assertInvariants(new BigDecimal("1.00"), a);
    }

    @Test
    void tinyAmount_fewerResidualCentsThanLines() {
        // 3¢ across 10 lines → three lines at 1¢, seven lines at 0¢.
        List<PaymentAllocation> a = run(new BigDecimal("0.03"), 10);
        for (int i = 0; i < 10; i++) {
            BigDecimal expected = i < 3 ? new BigDecimal("0.01") : BigDecimal.ZERO;
            assertEquals(0, expected.compareTo(a.get(i).getAllocatedAmount()),
                    "line " + i + " expected " + expected + " got " + a.get(i).getAllocatedAmount());
        }
        assertInvariants(new BigDecimal("0.03"), a);
    }

    @Test
    void manyLines_largePayment_reconcilesExactly() {
        // 100 lines, awkward $7.77 payment.
        List<PaymentAllocation> a = run(new BigDecimal("7.77"), 100);
        assertEquals(100, a.size());
        assertInvariants(new BigDecimal("7.77"), a);
        // Every allocation is within one cent of the exact even share (7¢ or 8¢).
        for (PaymentAllocation x : a) {
            int cents = x.getAllocatedAmount().movePointRight(2).intValueExact();
            assertTrue(cents == 7 || cents == 8,
                    "nearest-cent violation: " + x.getAllocatedAmount());
        }
    }

    @Test
    void singleLine_takesEntirePayment() {
        List<PaymentAllocation> a = run(new BigDecimal("42.73"), 1);
        assertEquals(0, new BigDecimal("42.73").compareTo(a.get(0).getAllocatedAmount()));
        assertInvariants(new BigDecimal("42.73"), a);
    }

    @Test
    void deterministic_sameInputsSameOutputs() {
        List<PaymentAllocation> a1 = run(new BigDecimal("13.37"), 7);
        reset(allocationRepo);
        List<PaymentAllocation> a2 = run(new BigDecimal("13.37"), 7);
        assertEquals(a1.size(), a2.size());
        for (int i = 0; i < a1.size(); i++) {
            assertEquals(0, a1.get(i).getAllocatedAmount()
                    .compareTo(a2.get(i).getAllocatedAmount()));
            assertEquals(a1.get(i).getResidualCents(), a2.get(i).getResidualCents());
        }
    }

    @Test
    void proportional_and_even_agreeOnUniformLines_forSingleCentPayment() {
        // Uniform line weights are the degenerate case where proportional
        // should effectively match even-split. 1¢ across 2 equal lines →
        // 1¢ on line 0, 0¢ on line 1.
        Payment p = new Payment();
        p.setId(1L); p.setAmount(new BigDecimal("0.01"));
        List<BillLineItem> lines = List.of(line(1L), line(2L));

        ArgumentCaptor<PaymentAllocation> cap = ArgumentCaptor.forClass(PaymentAllocation.class);
        when(allocationRepo.save(any(PaymentAllocation.class))).thenAnswer(i -> i.getArgument(0));
        svc.allocateEvenSplit(p, lines);
        verify(allocationRepo, times(2)).save(cap.capture());

        assertEquals(0, new BigDecimal("0.01").compareTo(cap.getAllValues().get(0).getAllocatedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(cap.getAllValues().get(1).getAllocatedAmount()));
    }
}
