package com.civicworks.dispatch;

import com.civicworks.dispatch.application.DispatchService;
import com.civicworks.dispatch.domain.*;
import com.civicworks.dispatch.infra.*;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * C) The driver-accept path must re-enforce the same constraints as the
 * grab path: rating >= 4.2, distance <= 3 miles, online >= 15 minutes.
 */
@ExtendWith(MockitoExtension.class)
class DispatchAcceptConstraintTest {

    @Mock private DispatchOrderRepository orderRepo;
    @Mock private DispatchAttemptRepository attemptRepo;
    @Mock private DriverRepository driverRepo;
    @Mock private DriverDailyPresenceRepository presenceRepo;
    @Mock private ZoneCapacityRuleRepository capacityRepo;
    @Mock private AuditService auditService;
    @Mock private MunicipalClock clock;

    private DispatchService svc;

    @BeforeEach
    void setUp() {
        svc = new DispatchService(orderRepo, attemptRepo, driverRepo, presenceRepo,
                capacityRepo, auditService, clock);
        lenient().when(clock.today()).thenReturn(LocalDate.of(2026, 4, 13));
    }

    private DispatchOrder makeAssignedOrder(Long driverId, boolean forced) {
        DispatchOrder o = new DispatchOrder();
        o.setId(50L);
        o.setStatus("ASSIGNED");
        o.setAssignedDriverId(driverId);
        o.setForced(forced);
        lenient().when(orderRepo.findById(50L)).thenReturn(Optional.of(o));
        lenient().when(orderRepo.save(any(DispatchOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        return o;
    }

    private Driver makeDriver(BigDecimal rating) {
        Driver d = new Driver();
        d.setId(7L);
        d.setUserId(1001L);
        d.setRating(rating);
        lenient().when(driverRepo.findByUserId(1001L)).thenReturn(Optional.of(d));
        return d;
    }

    private void stubPresence(int minutes) {
        DriverDailyPresence p = new DriverDailyPresence();
        p.setDriverId(7L);
        p.setPresenceDate(clock.today());
        p.setMinutesOnline(minutes);
        lenient().when(presenceRepo.findByDriverIdAndPresenceDate(eq(7L), any()))
                .thenReturn(Optional.of(p));
    }

    @Test
    void accept_failsWhenRatingDroppedBelowThreshold() {
        makeAssignedOrder(7L, false);
        makeDriver(new BigDecimal("4.10"));    // dropped after assignment
        stubPresence(60);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.driverResponse(50L, 1001L, true, null));
        assertTrue(ex.getMessage().toLowerCase().contains("rating"),
                "expected rating-related message, got: " + ex.getMessage());
        verify(orderRepo, never()).save(argThat(o -> "ACCEPTED".equals(o.getStatus())));
    }

    @Test
    void accept_failsWhenOnlineMinutesBelowThreshold() {
        makeAssignedOrder(7L, false);
        makeDriver(new BigDecimal("5.00"));
        stubPresence(10);                       // below 15

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.driverResponse(50L, 1001L, true, null));
        assertEquals("DRIVER_CONSTRAINT_FAILED", ex.getCode());
    }

    @Test
    void accept_succeedsWhenAllConstraintsMet() {
        DispatchOrder o = makeAssignedOrder(7L, false);
        makeDriver(new BigDecimal("4.50"));
        stubPresence(60);

        DispatchOrder result = svc.driverResponse(50L, 1001L, true, null);
        assertEquals("ACCEPTED", result.getStatus());
        verify(attemptRepo, times(1)).save(any(DispatchAttempt.class));
    }

    @Test
    void rejectForced_requiresEnumReason() {
        makeAssignedOrder(7L, true);
        makeDriver(new BigDecimal("5.00"));
        // No presence stub needed — reject path doesn't check constraints.

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.driverResponse(50L, 1001L, false, null));
        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    void rejectForced_succeedsWithReason() {
        makeAssignedOrder(7L, true);
        makeDriver(new BigDecimal("5.00"));

        DispatchOrder result = svc.driverResponse(50L, 1001L, false,
                RejectionReason.SAFETY_CONCERN);
        assertEquals("PENDING", result.getStatus());
        assertNull(result.getAssignedDriverId());
    }
}
