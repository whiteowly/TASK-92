package com.civicworks.billing;

import com.civicworks.billing.api.BillingController;
import com.civicworks.billing.application.BillingService;
import com.civicworks.billing.domain.FeeItem;
import com.civicworks.billing.infra.*;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires {@code taxable_flag} from the API DTO through {@link BillingService}
 * to the persisted {@link FeeItem}.
 */
@ExtendWith(MockitoExtension.class)
class FeeItemTaxableFlagTest {

    @Mock private FeeItemRepository feeItemRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private BillRepository billRepo;
    @Mock private BillLineItemRepository lineItemRepo;
    @Mock private BillDiscountRepository discountRepo;
    @Mock private BillLateFeeRepository lateFeeRepo;
    @Mock private BillingRunRepository runRepo;
    @Mock private BillingDueDatePolicyRepository policyRepo;
    @Mock private BillingUsageRepository usageRepo;
    @Mock private AuditService auditService;
    @Mock private MunicipalClock clock;

    private BillingService svc;

    @BeforeEach
    void setUp() {
        svc = new BillingService(feeItemRepo, accountRepo, billRepo, lineItemRepo,
                discountRepo, lateFeeRepo, runRepo, policyRepo, usageRepo,
                auditService, clock);
    }

    @Test
    void create_persistsTaxableFlagFromInput() {
        FeeItem input = new FeeItem();
        input.setCode("WASTE");
        input.setName("Waste collection");
        input.setCalculationType(FeeItem.CalculationType.FLAT);
        input.setRate(new BigDecimal("10.00"));
        input.setTaxableFlag(true);

        when(feeItemRepo.save(any(FeeItem.class))).thenAnswer(i -> i.getArgument(0));

        FeeItem saved = svc.createFeeItem(input, 1L);
        assertTrue(saved.isTaxableFlag(), "taxableFlag must be persisted as true");
    }

    @Test
    void update_overwritesTaxableFlag() {
        FeeItem existing = new FeeItem();
        existing.setName("old"); existing.setRate(new BigDecimal("5.00"));
        existing.setCalculationType(FeeItem.CalculationType.FLAT);
        existing.setActive(true); existing.setTaxableFlag(false);
        when(feeItemRepo.findById(7L)).thenReturn(Optional.of(existing));
        when(feeItemRepo.save(any(FeeItem.class))).thenAnswer(i -> i.getArgument(0));

        FeeItem updates = new FeeItem();
        updates.setName("new"); updates.setRate(new BigDecimal("6.00"));
        updates.setCalculationType(FeeItem.CalculationType.FLAT);
        updates.setActive(true); updates.setTaxableFlag(true);

        ArgumentCaptor<FeeItem> captor = ArgumentCaptor.forClass(FeeItem.class);
        svc.updateFeeItem(7L, updates, 1L);
        verify(feeItemRepo).save(captor.capture());
        assertTrue(captor.getValue().isTaxableFlag(),
                "update must propagate taxableFlag to the persisted entity");
    }

    @Test
    void dto_acceptsTaxableFlag_andDefaultsToFalseWhenOmitted() {
        // Pin the DTO contract: taxableFlag is part of the record and absent
        // payloads must be backward-compatible (treated as false).
        BillingController.FeeItemRequest omitted = new BillingController.FeeItemRequest(
                "X", "X", FeeItem.CalculationType.FLAT, new BigDecimal("1.00"),
                null, null);
        assertFalse(Boolean.TRUE.equals(omitted.taxableFlag()));

        BillingController.FeeItemRequest set = new BillingController.FeeItemRequest(
                "Y", "Y", FeeItem.CalculationType.FLAT, new BigDecimal("1.00"),
                null, true);
        assertTrue(set.taxableFlag());
    }
}
