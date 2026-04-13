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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * B) Zone capacity policy must be applied consistently to assign, grab, and
 * forced assignment alike. Forced assignment lets the dispatcher pick a
 * specific driver but does NOT let the zone exceed its capacity cap.
 */
@ExtendWith(MockitoExtension.class)
class DispatchCapacityTest {

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

    private DispatchOrder pendingOrderInZone(Long zoneId) {
        DispatchOrder o = new DispatchOrder();
        o.setId(50L); o.setStatus("PENDING"); o.setZoneId(zoneId);
        lenient().when(orderRepo.findById(50L)).thenReturn(Optional.of(o));
        lenient().when(orderRepo.save(any(DispatchOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        return o;
    }

    private void zoneAtCapacity(Long zoneId, int max) {
        ZoneCapacityRule r = new ZoneCapacityRule();
        r.setZoneId(zoneId); r.setMaxConcurrentAssignments(max);
        lenient().when(capacityRepo.findByZoneId(zoneId)).thenReturn(Optional.of(r));
        lenient().when(orderRepo.countActiveInZone(zoneId)).thenReturn(max);
    }

    private void zoneUnderCapacity(Long zoneId, int max) {
        ZoneCapacityRule r = new ZoneCapacityRule();
        r.setZoneId(zoneId); r.setMaxConcurrentAssignments(max);
        lenient().when(capacityRepo.findByZoneId(zoneId)).thenReturn(Optional.of(r));
        lenient().when(orderRepo.countActiveInZone(zoneId)).thenReturn(max - 1);
    }

    private Driver eligibleDriver() {
        Driver d = new Driver();
        d.setId(7L); d.setUserId(1001L); d.setRating(new BigDecimal("5.00"));
        lenient().when(driverRepo.findByUserId(1001L)).thenReturn(Optional.of(d));
        DriverDailyPresence p = new DriverDailyPresence();
        p.setDriverId(7L); p.setMinutesOnline(60); p.setPresenceDate(clock.today());
        lenient().when(presenceRepo.findByDriverIdAndPresenceDate(eq(7L), any()))
                .thenReturn(Optional.of(p));
        return d;
    }

    @Test
    void assign_failsWhenZoneAtCapacity() {
        pendingOrderInZone(100L);
        zoneAtCapacity(100L, 5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.assignOrder(50L, 7L, 1L));
        assertEquals("ZONE_CAPACITY_EXCEEDED", ex.getCode());
    }

    @Test
    void grab_failsWhenZoneAtCapacity() {
        pendingOrderInZone(100L);
        eligibleDriver();
        zoneAtCapacity(100L, 5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.grabOrder(50L, 1001L));
        assertEquals("ZONE_CAPACITY_EXCEEDED", ex.getCode());
        verify(orderRepo, never()).save(argThat(o -> "ACCEPTED".equals(o.getStatus())));
    }

    @Test
    void grab_succeedsWhenZoneUnderCapacity() {
        pendingOrderInZone(100L);
        eligibleDriver();
        zoneUnderCapacity(100L, 5);

        DispatchOrder result = svc.grabOrder(50L, 1001L);
        assertEquals("ACCEPTED", result.getStatus());
    }

    @Test
    void forcedAssign_failsWhenZoneAtCapacity() {
        pendingOrderInZone(100L);
        zoneAtCapacity(100L, 5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.forcedAssign(50L, 7L, 1L));
        assertEquals("ZONE_CAPACITY_EXCEEDED", ex.getCode());
        verify(orderRepo, never()).save(argThat(o -> "ASSIGNED".equals(o.getStatus())));
        verify(auditService, never()).log(any(), eq("DISPATCHER"),
                eq("ORDER_FORCED_ASSIGN"), any(), any(), any());
    }

    @Test
    void forcedAssign_succeedsWhenZoneUnderCapacity() {
        pendingOrderInZone(100L);
        zoneUnderCapacity(100L, 5);

        DispatchOrder result = svc.forcedAssign(50L, 7L, 1L);

        assertEquals("ASSIGNED", result.getStatus());
        assertTrue(result.isForced());
        ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(1L), eq("DISPATCHER"),
                eq("ORDER_FORCED_ASSIGN"), eq("dispatch_order"), eq("50"), after.capture());
        assertFalse(after.getValue().contains("capacityBypassed"),
                "capacity is enforced, no bypass marker expected: " + after.getValue());
    }
}
