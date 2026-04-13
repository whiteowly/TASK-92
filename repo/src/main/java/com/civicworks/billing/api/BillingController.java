package com.civicworks.billing.api;

import com.civicworks.billing.application.BillingService;
import com.civicworks.billing.domain.*;
import com.civicworks.platform.idempotency.IdempotencyService;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;
    private final IdempotencyService idempotencyService;

    public BillingController(BillingService billingService, IdempotencyService idempotencyService) {
        this.billingService = billingService;
        this.idempotencyService = idempotencyService;
    }

    // Fee items
    @PostMapping("/fee-items")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<FeeItem> createFeeItem(@Valid @RequestBody FeeItemRequest request) {
        FeeItem item = new FeeItem();
        item.setCode(request.code());
        item.setName(request.name());
        item.setCalculationType(request.calculationType());
        item.setRate(request.rate());
        // Backward-compatible default: omitted -> false (matches DB default).
        item.setTaxableFlag(Boolean.TRUE.equals(request.taxableFlag()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.createFeeItem(item, SecurityUtils.currentUserId()));
    }

    @PutMapping("/fee-items/{id}")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<FeeItem> updateFeeItem(@PathVariable Long id,
                                                   @Valid @RequestBody FeeItemRequest request) {
        FeeItem item = new FeeItem();
        item.setName(request.name());
        item.setCalculationType(request.calculationType());
        item.setRate(request.rate());
        item.setActive(request.active() != null ? request.active() : true);
        item.setTaxableFlag(Boolean.TRUE.equals(request.taxableFlag()));
        return ResponseEntity.ok(billingService.updateFeeItem(id, item, SecurityUtils.currentUserId()));
    }

    @GetMapping("/fee-items")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<List<FeeItem>> listFeeItems() {
        return ResponseEntity.ok(billingService.listFeeItems());
    }

    // Due date policy
    @GetMapping("/policies/due-date")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK', 'AUDITOR')")
    public ResponseEntity<BillingService.DueDatePolicyView> getDueDatePolicy() {
        return ResponseEntity.ok(billingService.getDueDatePolicy());
    }

    @PutMapping("/policies/due-date")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<BillingService.DueDatePolicyView> updateDueDatePolicy(
            @Valid @RequestBody DueDatePolicyRequest request) {
        return ResponseEntity.ok(billingService.updateDueDatePolicy(
                request.monthlyDueInDays(), request.quarterlyDueInDays(),
                request.effectiveFrom(), SecurityUtils.currentUserId()));
    }

    // Billing runs
    @PostMapping("/runs")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<BillingRun> executeBillingRun(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BillingRunRequest request) {
        return idempotencyService.executeIdempotent(
                "billing_run", idempotencyKey, request,
                () -> billingService.executeBillingRun(
                        request.cycleDate(), request.cycleType(), SecurityUtils.currentUserId()),
                BillingRun.class);
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAnyRole('BILLING_CLERK', 'AUDITOR')")
    public ResponseEntity<BillingRun> getBillingRun(@PathVariable Long runId) {
        return ResponseEntity.ok(billingService.getBillingRun(runId));
    }

    // Bills
    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('BILLING_CLERK', 'AUDITOR')")
    public ResponseEntity<Page<Bill>> listBills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(billingService.listBills(page, size));
    }

    @GetMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('BILLING_CLERK', 'AUDITOR')")
    public ResponseEntity<Bill> getBill(@PathVariable Long billId) {
        return ResponseEntity.ok(billingService.getBill(billId));
    }

    // Discounts
    @PostMapping("/bills/{billId}/discounts")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<BillDiscount> applyDiscount(
            @PathVariable Long billId, @Valid @RequestBody DiscountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.applyDiscount(billId, request.discountType(),
                        request.value(), SecurityUtils.currentUserId()));
    }

    public record FeeItemRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull FeeItem.CalculationType calculationType,
            @NotNull @DecimalMin("0") BigDecimal rate,
            Boolean active,
            Boolean taxableFlag
    ) {}

    public record DueDatePolicyRequest(
            @Min(1) @Max(60) int monthlyDueInDays,
            @Min(1) @Max(60) int quarterlyDueInDays,
            Instant effectiveFrom
    ) {}

    public record BillingRunRequest(
            @NotNull LocalDate cycleDate,
            @NotBlank String cycleType
    ) {}

    public record DiscountRequest(
            @NotBlank String discountType,
            @NotNull @DecimalMin("0") BigDecimal value
    ) {}
}
