package com.civicworks.settlement.api;

import com.civicworks.platform.idempotency.IdempotencyService;
import com.civicworks.platform.security.SecurityUtils;
import com.civicworks.settlement.application.SettlementService;
import com.civicworks.settlement.domain.*;
import com.civicworks.settlement.domain.PaymentMethod;
import com.civicworks.settlement.domain.SettlementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    public SettlementController(SettlementService settlementService, IdempotencyService idempotencyService) {
        this.settlementService = settlementService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<Payment> postPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return idempotencyService.executeIdempotent(
                "payment", idempotencyKey, request,
                () -> settlementService.postPayment(
                        request.billId(), request.amount(), request.method(),
                        request.settlementType(), SecurityUtils.currentUserId()),
                Payment.class);
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("hasRole('BILLING_CLERK')")
    public ResponseEntity<PaymentReversal> reversePayment(
            @PathVariable Long paymentId,
            @RequestBody ReverseRequest request) {
        return ResponseEntity.ok(settlementService.reversePayment(
                paymentId, request.reason(), SecurityUtils.currentUserId()));
    }

    @PostMapping("/shifts/{shiftId}/handover")
    @PreAuthorize("hasAnyRole('BILLING_CLERK', 'SYSTEM_ADMIN')")
    public ResponseEntity<ShiftHandoverReport> handover(@PathVariable Long shiftId) {
        return ResponseEntity.ok(settlementService.handover(shiftId, SecurityUtils.currentUserId()));
    }

    @GetMapping("/discrepancies")
    @PreAuthorize("hasAnyRole('BILLING_CLERK', 'AUDITOR', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<DiscrepancyCase>> listDiscrepancies() {
        return ResponseEntity.ok(settlementService.listDiscrepancies());
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<DiscrepancyCase> resolveDiscrepancy(
            @PathVariable Long id, @RequestBody ResolveRequest request) {
        return ResponseEntity.ok(settlementService.resolveDiscrepancy(
                id, request.notes(), SecurityUtils.currentUserId()));
    }

    public record PaymentRequest(
            @NotNull Long billId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull PaymentMethod method,
            @NotNull SettlementType settlementType
    ) {}

    public record ReverseRequest(String reason) {}
    public record ResolveRequest(String notes) {}
}
