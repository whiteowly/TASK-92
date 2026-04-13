package com.civicworks.settlement.application;

import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.Bill.BillStatus;
import com.civicworks.billing.domain.BillLineItem;
import com.civicworks.billing.infra.BillLineItemRepository;
import com.civicworks.billing.infra.BillRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import com.civicworks.settlement.domain.*;
import com.civicworks.settlement.infra.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class SettlementService {

    private static final BigDecimal DISCREPANCY_THRESHOLD = new BigDecimal("1.00");

    private final PaymentRepository paymentRepo;
    private final PaymentAllocationRepository allocationRepo;
    private final PaymentReversalRepository reversalRepo;
    private final CashShiftRepository shiftRepo;
    private final ShiftHandoverReportRepository handoverRepo;
    private final DiscrepancyCaseRepository discrepancyRepo;
    private final BillRepository billRepo;
    private final BillLineItemRepository lineItemRepo;
    private final AuditService auditService;

    public SettlementService(PaymentRepository paymentRepo, PaymentAllocationRepository allocationRepo,
                             PaymentReversalRepository reversalRepo, CashShiftRepository shiftRepo,
                             ShiftHandoverReportRepository handoverRepo, DiscrepancyCaseRepository discrepancyRepo,
                             BillRepository billRepo, BillLineItemRepository lineItemRepo,
                             AuditService auditService) {
        this.paymentRepo = paymentRepo;
        this.allocationRepo = allocationRepo;
        this.reversalRepo = reversalRepo;
        this.shiftRepo = shiftRepo;
        this.handoverRepo = handoverRepo;
        this.discrepancyRepo = discrepancyRepo;
        this.billRepo = billRepo;
        this.lineItemRepo = lineItemRepo;
        this.auditService = auditService;
    }

    @Transactional
    public Payment postPayment(Long billId, BigDecimal amount, PaymentMethod method,
                               SettlementType settlementType, Long actorId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> BusinessException.notFound("Bill not found"));

        if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.CLOSED) {
            throw BusinessException.conflict(ErrorCode.BILL_ALREADY_SETTLED, "Bill is already settled");
        }

        if (amount.compareTo(bill.getBalance()) > 0) {
            throw BusinessException.unprocessable(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Payment exceeds bill balance");
        }

        // FULL settlement must clear the current bill balance exactly. Partial
        // amounts must use SPLIT or EVEN_SPLIT. Compare with compareTo so
        // differing BigDecimal scales (e.g. 50 vs 50.00) don't spuriously fail.
        if (settlementType == SettlementType.FULL
                && amount.compareTo(bill.getBalance()) != 0) {
            throw BusinessException.unprocessable(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "FULL settlement requires payment equal to the current bill balance ("
                            + bill.getBalance() + ")");
        }

        Payment payment = new Payment();
        payment.setBillId(billId);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setSettlementType(settlementType);
        payment.setPostedBy(actorId);
        // Tag the payment with the operator's open shift, if any, so handover
        // can scope sums precisely by shift_id rather than only by time window.
        if (actorId != null) {
            Long openShiftId = shiftRepo.findOpenShiftForOperator(actorId)
                    .map(CashShift::getId)
                    .orElse(null);
            payment.setShiftId(openShiftId);
        }
        payment = paymentRepo.save(payment);

        // Allocate payment
        List<BillLineItem> lineItems = lineItemRepo.findByBillIdOrderByLineOrderAsc(billId);
        allocatePayment(payment, lineItems, bill, settlementType);

        // Update bill
        String before = "balance=" + bill.getBalance();
        bill.setPaidAmount(bill.getPaidAmount().add(amount));
        bill.recalculateBalance();
        billRepo.save(bill);

        auditService.log(actorId, "BILLING_CLERK", "PAYMENT_POST", "bill",
                billId.toString(), before + " -> balance=" + bill.getBalance());
        return payment;
    }

    private void allocatePayment(Payment payment, List<BillLineItem> lineItems,
                                  Bill bill, SettlementType settlementType) {
        if (settlementType == SettlementType.EVEN_SPLIT && !lineItems.isEmpty()) {
            allocateEvenSplit(payment, lineItems);
        } else if (settlementType == SettlementType.SPLIT && !lineItems.isEmpty()) {
            allocateProportional(payment, lineItems, bill);
        } else {
            // Full payment - single allocation
            PaymentAllocation alloc = new PaymentAllocation();
            alloc.setPaymentId(payment.getId());
            alloc.setAllocatedAmount(payment.getAmount());
            alloc.setAllocationOrder(0);
            allocationRepo.save(alloc);
        }
    }

    /**
     * Even-split allocation with nearest-cent policy and deterministic
     * residual distribution.
     *
     * <p>Every line gets the integer quotient of the payment (in cents)
     * divided by the line count; the remainder cents are distributed one
     * cent at a time to the earliest lines (deterministic by
     * {@code allocation_order}). The per-line {@code residual_cents}
     * column records which lines absorbed the residual cent so reporting
     * can reconcile against the float-quotient.
     *
     * <p>Invariants:
     * <ul>
     *   <li>All allocations are &gt;= 0.</li>
     *   <li>Every allocation is within one cent of the exact even share
     *       (nearest $0.01 policy).</li>
     *   <li>Sum(allocations) == payment.amount exactly.</li>
     *   <li>Deterministic: same inputs → same outputs.</li>
     * </ul>
     */
    void allocateEvenSplit(Payment payment, List<BillLineItem> lineItems) {
        int count = lineItems.size();
        BigDecimal total = payment.getAmount();
        // Convert payment amount to an integer cent count, rounding HALF_UP
        // at the "cents" boundary — this is the nearest-cent discretisation
        // for the overall amount before we split it.
        long totalCents = total.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        long baseCents = totalCents / count;
        long residualCents = totalCents - baseCents * count; // 0..count-1

        for (int i = 0; i < count; i++) {
            BillLineItem li = lineItems.get(i);
            long lineCents = baseCents + (i < residualCents ? 1 : 0);
            int residual = (i < residualCents) ? 1 : 0;

            PaymentAllocation alloc = new PaymentAllocation();
            alloc.setPaymentId(payment.getId());
            alloc.setLineItemId(li.getId());
            alloc.setAllocatedAmount(BigDecimal.valueOf(lineCents, 2));
            alloc.setAllocationOrder(i);
            alloc.setResidualCents(residual);
            allocationRepo.save(alloc);
        }
    }

    /**
     * Proportional split allocation using the nearest-cent (HALF_UP) policy
     * combined with the Hamilton / largest-remainder method for exact
     * reconciliation:
     * <ol>
     *   <li>Compute each line's raw allocation (paymentAmount * weight) in
     *       cents, as a scaled {@link BigDecimal}.</li>
     *   <li>Floor each to whole cents; track the fractional remainder.</li>
     *   <li>Distribute the residual cents (paymentAmount - sum(floored))
     *       deterministically to lines with the largest fractional remainders
     *       (ties broken by {@link BillLineItem} order).</li>
     * </ol>
     * Invariants:
     * <ul>
     *   <li>Every allocation is &gt;= 0 (no negative cents under any input).</li>
     *   <li>Sum of allocations == payment amount exactly.</li>
     *   <li>Individual allocations are within one cent of the ideal nearest-
     *       cent value, satisfying the "nearest $0.01" rounding policy.</li>
     *   <li>Deterministic: same inputs → same outputs on every run.</li>
     * </ul>
     */
    void allocateProportional(Payment payment, List<BillLineItem> lineItems, Bill bill) {
        BigDecimal weightDenominator = bill.getOriginalAmount();
        if (weightDenominator == null || weightDenominator.compareTo(BigDecimal.ZERO) <= 0) return;
        int n = lineItems.size();
        if (n == 0) return;

        BigDecimal paymentAmount = payment.getAmount();
        // Work in integer cents to avoid double-rounding and make the
        // residual distribution exact.
        long totalCents = paymentAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        long[] floorCents = new long[n];
        BigDecimal[] remainders = new BigDecimal[n];
        long sumFloor = 0L;

        for (int i = 0; i < n; i++) {
            BigDecimal weight = lineItems.get(i).getAmount()
                    .divide(weightDenominator, 20, RoundingMode.HALF_UP);
            BigDecimal rawCents = paymentAmount.multiply(weight).movePointRight(2);
            long floor = rawCents.setScale(0, RoundingMode.FLOOR).longValueExact();
            if (floor < 0) floor = 0L;
            floorCents[i] = floor;
            remainders[i] = rawCents.subtract(BigDecimal.valueOf(floor));
            sumFloor += floor;
        }

        long residual = totalCents - sumFloor;
        // residual >= 0 holds because each rawCents >= floor and totalCents ==
        // sum(rawCents) (within rounding of the weight division). If weight
        // division introduces a tiny positive deficit the residual stays >= 0.
        // If residual > 0, hand out +1 cent to the lines with the largest
        // fractional remainder; ties break by line order (i ascending).
        if (residual > 0) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            BigDecimal[] finalRemainders = remainders;
            java.util.Arrays.sort(order, (a, b) -> {
                int cmp = finalRemainders[b].compareTo(finalRemainders[a]);
                return cmp != 0 ? cmp : Integer.compare(a, b);
            });
            for (int k = 0; k < residual && k < n; k++) {
                floorCents[order[k]] += 1;
            }
        } else if (residual < 0) {
            // Rare: weight-division rounding overshot. Remove cents from the
            // lines with smallest fractional remainder (and only from lines
            // that can afford it) to reconcile exactly. Deterministic order.
            long need = -residual;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            BigDecimal[] finalRemainders = remainders;
            java.util.Arrays.sort(order, (a, b) -> {
                int cmp = finalRemainders[a].compareTo(finalRemainders[b]);
                return cmp != 0 ? cmp : Integer.compare(a, b);
            });
            for (int k = 0; k < order.length && need > 0; k++) {
                int idx = order[k];
                if (floorCents[idx] > 0) {
                    long take = Math.min(floorCents[idx], need);
                    floorCents[idx] -= take;
                    need -= take;
                }
            }
        }

        for (int i = 0; i < n; i++) {
            BigDecimal alloc = BigDecimal.valueOf(floorCents[i], 2);
            PaymentAllocation pa = new PaymentAllocation();
            pa.setPaymentId(payment.getId());
            pa.setLineItemId(lineItems.get(i).getId());
            pa.setAllocatedAmount(alloc);
            pa.setAllocationOrder(i);
            allocationRepo.save(pa);
        }
    }

    @Transactional
    public PaymentReversal reversePayment(Long paymentId, String reason, Long actorId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found"));

        if (payment.isReversed()) {
            throw BusinessException.conflict(ErrorCode.REVERSAL_ALREADY_EXISTS, "Payment already reversed");
        }

        reversalRepo.findByOriginalPaymentId(paymentId).ifPresent(r -> {
            throw BusinessException.conflict(ErrorCode.REVERSAL_ALREADY_EXISTS, "Reversal already exists");
        });

        payment.setReversed(true);
        paymentRepo.save(payment);

        Bill bill = billRepo.findById(payment.getBillId())
                .orElseThrow(() -> BusinessException.notFound("Bill not found"));
        String before = "paid=" + bill.getPaidAmount();
        bill.setPaidAmount(bill.getPaidAmount().subtract(payment.getAmount()));
        bill.recalculateBalance();
        if (bill.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.OPEN);
        }
        billRepo.save(bill);

        PaymentReversal reversal = new PaymentReversal();
        reversal.setOriginalPaymentId(paymentId);
        reversal.setReversalAmount(payment.getAmount());
        reversal.setReason(reason);
        reversal.setReversedBy(actorId);
        PaymentReversal saved = reversalRepo.save(reversal);

        auditService.log(actorId, "BILLING_CLERK", "PAYMENT_REVERSE", "payment",
                paymentId.toString(), before + " -> reversal=" + payment.getAmount());
        return saved;
    }

    @Transactional
    public ShiftHandoverReport handover(Long shiftId, Long actorId) {
        CashShift shift = shiftRepo.findById(shiftId)
                .orElseThrow(() -> BusinessException.notFound("Shift not found"));

        Instant from = shift.getStartedAt();
        Instant to = shift.getEndedAt() != null ? shift.getEndedAt() : Instant.now();

        // Prefer shift_id-scoped sums (precise) and fall back to operator+window
        // sums for any legacy payments without a shift_id tag.
        BigDecimal cashTotal    = paymentRepo.sumByMethodForShiftId(shiftId, PaymentMethod.CASH);
        BigDecimal checkTotal   = paymentRepo.sumByMethodForShiftId(shiftId, PaymentMethod.CHECK);
        BigDecimal voucherTotal = paymentRepo.sumByMethodForShiftId(shiftId, PaymentMethod.VOUCHER);
        BigDecimal otherTotal   = paymentRepo.sumByMethodForShiftId(shiftId, PaymentMethod.OTHER);

        BigDecimal legacyCash    = paymentRepo.sumByMethodForShift(shift.getOperatorId(), PaymentMethod.CASH,    from, to);
        BigDecimal legacyCheck   = paymentRepo.sumByMethodForShift(shift.getOperatorId(), PaymentMethod.CHECK,   from, to);
        BigDecimal legacyVoucher = paymentRepo.sumByMethodForShift(shift.getOperatorId(), PaymentMethod.VOUCHER, from, to);
        BigDecimal legacyOther   = paymentRepo.sumByMethodForShift(shift.getOperatorId(), PaymentMethod.OTHER,   from, to);
        cashTotal    = cashTotal.add(legacyCash);
        checkTotal   = checkTotal.add(legacyCheck);
        voucherTotal = voucherTotal.add(legacyVoucher);
        otherTotal   = otherTotal.add(legacyOther);

        BigDecimal paymentTotal = cashTotal.add(checkTotal).add(voucherTotal).add(otherTotal);

        // Posted A/R basis: gross sum of every payment posted during the shift,
        // including reversed ones. paymentTotal above is net (excludes reversed).
        // A non-zero |paymentTotal - postedArTotal| therefore reflects real
        // discrepancy-worthy activity (reversed postings) inside the shift.
        BigDecimal postedArTotal = paymentRepo.sumPostedArForShiftId(shiftId)
                .add(paymentRepo.sumPostedArForShift(shift.getOperatorId(), from, to));
        BigDecimal variance = paymentTotal.subtract(postedArTotal).abs();

        ShiftHandoverReport report = new ShiftHandoverReport();
        report.setShiftId(shiftId);
        report.setCashTotal(cashTotal);
        report.setCheckTotal(checkTotal);
        report.setVoucherTotal(voucherTotal);
        report.setOtherTotal(otherTotal);
        report.setPostedArTotal(postedArTotal);
        report.setVariance(variance);
        report = handoverRepo.save(report);

        shift.setStatus("CLOSED");
        shift.setEndedAt(to);
        shiftRepo.save(shift);

        if (variance.compareTo(DISCREPANCY_THRESHOLD) > 0) {
            DiscrepancyCase dc = new DiscrepancyCase();
            dc.setHandoverReportId(report.getId());
            dc.setVarianceAmount(variance);
            discrepancyRepo.save(dc);
        }

        auditService.log(actorId, "BILLING_CLERK", "SHIFT_HANDOVER", "cash_shift",
                shiftId.toString(), "variance=" + variance);
        return report;
    }

    public List<DiscrepancyCase> listDiscrepancies() {
        return discrepancyRepo.findAll();
    }

    @Transactional
    public DiscrepancyCase resolveDiscrepancy(Long id, String notes, Long actorId) {
        DiscrepancyCase dc = discrepancyRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Discrepancy not found"));
        dc.setStatus("RESOLVED");
        dc.setResolutionNotes(notes);
        dc.setResolvedBy(actorId);
        dc.setResolvedAt(Instant.now());
        DiscrepancyCase saved = discrepancyRepo.save(dc);
        auditService.log(actorId, "SYSTEM_ADMIN", "DISCREPANCY_RESOLVE", "discrepancy_case",
                id.toString(), "notes=" + notes);
        return saved;
    }
}
