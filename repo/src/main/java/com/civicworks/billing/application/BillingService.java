package com.civicworks.billing.application;

import com.civicworks.billing.domain.*;
import com.civicworks.billing.domain.Bill.BillStatus;
import com.civicworks.billing.infra.*;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class BillingService {

    private static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.05");
    private static final BigDecimal LATE_FEE_CAP = new BigDecimal("50.00");
    private static final int GRACE_PERIOD_DAYS = 10;

    private final FeeItemRepository feeItemRepo;
    private final AccountRepository accountRepo;
    private final BillRepository billRepo;
    private final BillLineItemRepository lineItemRepo;
    private final BillDiscountRepository discountRepo;
    private final BillLateFeeRepository lateFeeRepo;
    private final BillingRunRepository runRepo;
    private final BillingDueDatePolicyRepository policyRepo;
    private final BillingUsageRepository usageRepo;
    private final AuditService auditService;
    private final MunicipalClock clock;

    public BillingService(FeeItemRepository feeItemRepo, AccountRepository accountRepo,
                          BillRepository billRepo, BillLineItemRepository lineItemRepo,
                          BillDiscountRepository discountRepo, BillLateFeeRepository lateFeeRepo,
                          BillingRunRepository runRepo, BillingDueDatePolicyRepository policyRepo,
                          BillingUsageRepository usageRepo,
                          AuditService auditService, MunicipalClock clock) {
        this.feeItemRepo = feeItemRepo;
        this.accountRepo = accountRepo;
        this.billRepo = billRepo;
        this.lineItemRepo = lineItemRepo;
        this.discountRepo = discountRepo;
        this.lateFeeRepo = lateFeeRepo;
        this.runRepo = runRepo;
        this.usageRepo = usageRepo;
        this.policyRepo = policyRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    // Fee items
    @Transactional
    public FeeItem createFeeItem(FeeItem item, Long actorId) {
        FeeItem saved = feeItemRepo.save(item);
        auditService.log(actorId, "BILLING_CLERK", "FEE_ITEM_CREATE", "fee_item",
                saved.getId() != null ? saved.getId().toString() : null, null);
        return saved;
    }

    @Transactional
    public FeeItem updateFeeItem(Long id, FeeItem updates, Long actorId) {
        FeeItem existing = feeItemRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Fee item not found"));
        existing.setName(updates.getName());
        existing.setCalculationType(updates.getCalculationType());
        existing.setRate(updates.getRate());
        existing.setActive(updates.isActive());
        existing.setTaxableFlag(updates.isTaxableFlag());
        FeeItem saved = feeItemRepo.save(existing);
        auditService.log(actorId, "BILLING_CLERK", "FEE_ITEM_UPDATE", "fee_item",
                id.toString(), null);
        return saved;
    }

    public List<FeeItem> listFeeItems() {
        return feeItemRepo.findAll();
    }

    // Due date policy
    public DueDatePolicyView getDueDatePolicy() {
        BillingDueDatePolicy monthly = policyRepo.findLatestPolicy("MONTHLY")
                .orElse(null);
        BillingDueDatePolicy quarterly = policyRepo.findLatestPolicy("QUARTERLY")
                .orElse(null);
        return new DueDatePolicyView(
                monthly != null ? monthly.getDueInDays() : 15,
                quarterly != null ? quarterly.getDueInDays() : 15,
                monthly != null ? monthly.getEffectiveFrom() : null,
                quarterly != null ? quarterly.getEffectiveFrom() : null
        );
    }

    @Transactional
    public DueDatePolicyView updateDueDatePolicy(int monthlyDueInDays, int quarterlyDueInDays,
                                                  Instant effectiveFrom, Long actorId) {
        if (monthlyDueInDays < 1 || monthlyDueInDays > 60 || quarterlyDueInDays < 1 || quarterlyDueInDays > 60) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "dueInDays must be 1..60");
        }
        Instant eff = effectiveFrom != null ? effectiveFrom : clock.instant();

        BillingDueDatePolicy mp = new BillingDueDatePolicy();
        mp.setCycleType("MONTHLY");
        mp.setDueInDays(monthlyDueInDays);
        mp.setEffectiveFrom(eff);
        mp.setCreatedBy(actorId);
        policyRepo.save(mp);

        BillingDueDatePolicy qp = new BillingDueDatePolicy();
        qp.setCycleType("QUARTERLY");
        qp.setDueInDays(quarterlyDueInDays);
        qp.setEffectiveFrom(eff);
        qp.setCreatedBy(actorId);
        policyRepo.save(qp);

        auditService.log(actorId, "SYSTEM_ADMIN", "DUE_DATE_POLICY_UPDATE", "billing_policy",
                null, "monthly=" + monthlyDueInDays + ",quarterly=" + quarterlyDueInDays);

        return getDueDatePolicy();
    }

    // Billing run
    @Transactional
    public BillingRun executeBillingRun(LocalDate cycleDate, String cycleType, Long actorId) {
        Instant now = clock.instant();
        BillingDueDatePolicy policy = policyRepo.findActivePolicy(cycleType, now)
                .orElseThrow(() -> BusinessException.notFound("No active due-date policy for " + cycleType));

        LocalDate dueDate = cycleDate.plusDays(policy.getDueInDays());

        BillingRun run = new BillingRun();
        run.setCycleDate(cycleDate);
        run.setCycleType(cycleType);
        run.setPolicyDueInDays(policy.getDueInDays());
        run.setPolicyEffectiveFrom(policy.getEffectiveFrom());
        run = runRepo.save(run);

        List<Account> accounts = accountRepo.findByStatus("ACTIVE");
        List<FeeItem> activeFees = feeItemRepo.findByActiveTrue();

        BigDecimal totalAmount = BigDecimal.ZERO;
        int billCount = 0;

        for (Account account : accounts) {
            BigDecimal billTotal = BigDecimal.ZERO;
            int lineOrder = 0;

            Bill bill = new Bill();
            bill.setBillingRunId(run.getId());
            bill.setAccountId(account.getId());
            bill.setCycleDate(cycleDate);
            bill.setCycleType(cycleType);
            bill.setDueDate(dueDate);
            bill.setPolicyDueInDays(policy.getDueInDays());
            bill.setOriginalAmount(BigDecimal.ZERO);
            bill.setBalance(BigDecimal.ZERO);
            bill = billRepo.save(bill);

            for (FeeItem fee : activeFees) {
                BigDecimal usageUnits = usageRepo
                        .findLatestUpTo(account.getId(), fee.getId(), cycleDate)
                        .map(BillingUsage::getUnits)
                        .orElse(null);
                BillingCalculator.LineCalc calc = BillingCalculator.calculate(fee, usageUnits);
                BillLineItem lineItem = new BillLineItem();
                lineItem.setBillId(bill.getId());
                lineItem.setFeeItemId(fee.getId());
                lineItem.setDescription(fee.getName());
                lineItem.setQuantity(calc.quantity());
                lineItem.setUnitRate(calc.unitRate());
                lineItem.setAmount(calc.amount());
                lineItem.setLineOrder(lineOrder++);
                lineItemRepo.save(lineItem);
                billTotal = billTotal.add(calc.amount());
            }

            bill.setOriginalAmount(billTotal);
            bill.setBalance(billTotal);
            billRepo.save(bill);

            totalAmount = totalAmount.add(billTotal);
            billCount++;
        }

        run.setBillsGenerated(billCount);
        run.setTotalAmount(totalAmount);
        run.setStatus("COMPLETED");
        run.setCompletedAt(clock.instant());
        runRepo.save(run);

        auditService.log(actorId, "BILLING_CLERK", "BILLING_RUN", "billing_run",
                run.getId().toString(), "bills=" + billCount + ",total=" + totalAmount);
        return run;
    }

    public BillingRun getBillingRun(Long runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> BusinessException.notFound("Billing run not found"));
    }

    public Page<Bill> listBills(int page, int size) {
        return billRepo.findAll(PageRequest.of(page, Math.min(size, 100)));
    }

    public Bill getBill(Long billId) {
        return billRepo.findById(billId)
                .orElseThrow(() -> BusinessException.notFound("Bill not found"));
    }

    // Discounts
    @Transactional
    public BillDiscount applyDiscount(Long billId, String discountType, BigDecimal value, Long actorId) {
        Bill bill = getBill(billId);
        BigDecimal appliedAmount;

        if ("PERCENTAGE".equals(discountType)) {
            appliedAmount = bill.getOriginalAmount()
                    .multiply(value)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            appliedAmount = value.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal newDiscountTotal = bill.getDiscountAmount().add(appliedAmount);
        if (newDiscountTotal.compareTo(bill.getOriginalAmount().add(bill.getLateFeeAmount())) > 0) {
            throw BusinessException.unprocessable(ErrorCode.BILL_BELOW_ZERO,
                    "Discount would reduce bill below $0.00");
        }

        bill.setDiscountAmount(newDiscountTotal);
        bill.recalculateBalance();
        billRepo.save(bill);

        BillDiscount discount = new BillDiscount();
        discount.setBillId(billId);
        discount.setDiscountType(discountType);
        discount.setValue(value);
        discount.setAppliedAmount(appliedAmount);
        discount.setAppliedBy(actorId);
        BillDiscount saved = discountRepo.save(discount);

        auditService.log(actorId, "BILLING_CLERK", "DISCOUNT_APPLY", "bill",
                billId.toString(), "type=" + discountType + ",amount=" + appliedAmount);
        return saved;
    }

    // Late fees
    @Transactional
    public void processLateFees() {
        LocalDate today = clock.today();
        LocalDate cutoffDate = today.minusDays(GRACE_PERIOD_DAYS);
        List<Bill> eligible = billRepo.findEligibleForLateFee(cutoffDate);

        for (Bill bill : eligible) {
            BigDecimal existingLateFees = lateFeeRepo.sumByBillId(bill.getId());
            if (existingLateFees.compareTo(LATE_FEE_CAP) >= 0) {
                continue;
            }

            BigDecimal feeBase = bill.getOriginalAmount().subtract(bill.getDiscountAmount());
            BigDecimal fee = feeBase.multiply(LATE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal remainingCap = LATE_FEE_CAP.subtract(existingLateFees);
            fee = fee.min(remainingCap);

            if (fee.compareTo(BigDecimal.ZERO) <= 0) continue;

            BillLateFee lateFee = new BillLateFee();
            lateFee.setBillId(bill.getId());
            lateFee.setFeeAmount(fee);
            lateFee.setEligibleDate(cutoffDate);
            lateFeeRepo.save(lateFee);

            bill.setLateFeeAmount(existingLateFees.add(fee));
            bill.setStatus(BillStatus.OVERDUE);
            bill.recalculateBalance();
            billRepo.save(bill);

            auditService.log(null, "SYSTEM", "LATE_FEE_APPLY", "bill",
                    bill.getId().toString(), "fee=" + fee);
        }
    }

    public record DueDatePolicyView(
            int monthlyDueInDays,
            int quarterlyDueInDays,
            Instant monthlyEffectiveFrom,
            Instant quarterlyEffectiveFrom
    ) {}
}
