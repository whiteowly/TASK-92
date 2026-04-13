package com.civicworks.settlement;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.Bill.BillStatus;
import com.civicworks.billing.infra.BillLineItemRepository;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import com.civicworks.settlement.application.SettlementService;
import com.civicworks.settlement.domain.*;
import com.civicworks.settlement.infra.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FULL settlement must clear the current bill balance exactly. Partial
 * amounts on a FULL-typed payment must be rejected as 4xx with the
 * project's PAYMENT_AMOUNT_MISMATCH code. SPLIT / EVEN_SPLIT are unaffected.
 */
class SettlementFullSemanticsTest {

    private BillRepository billRepo;
    private PaymentRepository paymentRepo;
    private PaymentAllocationRepository allocRepo;
    private PaymentReversalRepository reversalRepo;
    private CashShiftRepository shiftRepo;
    private ShiftHandoverReportRepository handoverRepo;
    private DiscrepancyCaseRepository discrepancyRepo;
    private BillLineItemRepository lineItemRepo;
    private AuditService auditService;
    private SettlementService svc;

    @BeforeEach
    void setUp() {
        billRepo = mock(BillRepository.class);
        paymentRepo = mock(PaymentRepository.class);
        allocRepo = mock(PaymentAllocationRepository.class);
        reversalRepo = mock(PaymentReversalRepository.class);
        shiftRepo = mock(CashShiftRepository.class);
        handoverRepo = mock(ShiftHandoverReportRepository.class);
        discrepancyRepo = mock(DiscrepancyCaseRepository.class);
        lineItemRepo = mock(BillLineItemRepository.class);
        auditService = mock(AuditService.class);
        svc = new SettlementService(paymentRepo, allocRepo, reversalRepo, shiftRepo,
                handoverRepo, discrepancyRepo, billRepo, lineItemRepo, auditService);
    }

    private Bill billWithBalance(BigDecimal balance) {
        Bill b = new Bill();
        b.setId(100L);
        b.setAccountId(1L);
        b.setOriginalAmount(balance);
        b.setBalance(balance);
        b.setPaidAmount(BigDecimal.ZERO);
        b.setDiscountAmount(BigDecimal.ZERO);
        b.setLateFeeAmount(BigDecimal.ZERO);
        b.setStatus(BillStatus.OPEN);
        return b;
    }

    @Test
    void fullSettlement_rejectsPartialAmount_with422() {
        Bill bill = billWithBalance(new BigDecimal("100.00"));
        when(billRepo.findById(100L)).thenReturn(Optional.of(bill));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.postPayment(100L, new BigDecimal("50.00"),
                        PaymentMethod.CASH, SettlementType.FULL, 9L));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals(ErrorCode.PAYMENT_AMOUNT_MISMATCH, ex.getCode());
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void fullSettlement_acceptsExactBalance() {
        Bill bill = billWithBalance(new BigDecimal("100.00"));
        when(billRepo.findById(100L)).thenReturn(Optional.of(bill));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(7L);
            return p;
        });
        when(lineItemRepo.findByBillIdOrderByLineOrderAsc(100L)).thenReturn(List.of());
        when(shiftRepo.findOpenShiftForOperator(9L)).thenReturn(Optional.empty());

        Payment p = svc.postPayment(100L, new BigDecimal("100.00"),
                PaymentMethod.CASH, SettlementType.FULL, 9L);

        assertNotNull(p);
        assertEquals(0, p.getAmount().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void splitAllowsPartialAmounts() {
        Bill bill = billWithBalance(new BigDecimal("100.00"));
        when(billRepo.findById(100L)).thenReturn(Optional.of(bill));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(8L); return p;
        });
        when(lineItemRepo.findByBillIdOrderByLineOrderAsc(100L)).thenReturn(List.of());
        when(shiftRepo.findOpenShiftForOperator(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> svc.postPayment(100L, new BigDecimal("40.00"),
                PaymentMethod.CASH, SettlementType.SPLIT, 9L));
    }

    @Test
    void evenSplitAllowsPartialAmounts() {
        Bill bill = billWithBalance(new BigDecimal("100.00"));
        when(billRepo.findById(100L)).thenReturn(Optional.of(bill));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(9L); return p;
        });
        when(lineItemRepo.findByBillIdOrderByLineOrderAsc(100L)).thenReturn(List.of());
        when(shiftRepo.findOpenShiftForOperator(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> svc.postPayment(100L, new BigDecimal("25.00"),
                PaymentMethod.CASH, SettlementType.EVEN_SPLIT, 9L));
    }

    @Test
    void fullSettlement_scaleMismatchStillEquals() {
        // BigDecimal 100 vs 100.00 must compare equal via compareTo.
        Bill bill = billWithBalance(new BigDecimal("100.00"));
        when(billRepo.findById(100L)).thenReturn(Optional.of(bill));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(10L); return p;
        });
        when(lineItemRepo.findByBillIdOrderByLineOrderAsc(100L)).thenReturn(List.of());
        when(shiftRepo.findOpenShiftForOperator(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> svc.postPayment(100L, new BigDecimal("100"),
                PaymentMethod.CASH, SettlementType.FULL, 9L));
    }
}
