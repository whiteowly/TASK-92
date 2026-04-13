package com.civicworks.dispatch.application;

import com.civicworks.dispatch.domain.*;
import com.civicworks.dispatch.infra.*;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.clock.MunicipalClock;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DispatchService {

    private static final BigDecimal MAX_GRAB_DISTANCE_MILES = new BigDecimal("3.0");
    private static final BigDecimal MIN_RATING = new BigDecimal("4.20");
    private static final int MIN_ONLINE_MINUTES = 15;
    private static final int FORCED_COOLDOWN_MINUTES = 30;

    private final DispatchOrderRepository orderRepo;
    private final DispatchAttemptRepository attemptRepo;
    private final DriverRepository driverRepo;
    private final DriverDailyPresenceRepository presenceRepo;
    private final ZoneCapacityRuleRepository capacityRepo;
    private final AuditService auditService;
    private final MunicipalClock clock;

    public DispatchService(DispatchOrderRepository orderRepo, DispatchAttemptRepository attemptRepo,
                           DriverRepository driverRepo, DriverDailyPresenceRepository presenceRepo,
                           ZoneCapacityRuleRepository capacityRepo, AuditService auditService,
                           MunicipalClock clock) {
        this.orderRepo = orderRepo;
        this.attemptRepo = attemptRepo;
        this.driverRepo = driverRepo;
        this.presenceRepo = presenceRepo;
        this.capacityRepo = capacityRepo;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DispatchOrder createOrder(DispatchOrder order, Long actorId) {
        order.setCreatedBy(actorId);
        order.setStatus("PENDING");
        DispatchOrder saved = orderRepo.save(order);
        auditService.log(actorId, "DISPATCHER", "ORDER_CREATE", "dispatch_order",
                saved.getId().toString(), null);
        return saved;
    }

    public Page<DispatchOrder> listOrders(int page, int size) {
        return orderRepo.findAll(PageRequest.of(page, Math.min(size, 100)));
    }

    public Page<DispatchOrder> listDriverOrders(Long driverId, int page, int size) {
        return orderRepo.findActiveByDriver(driverId, PageRequest.of(page, Math.min(size, 100)));
    }

    /**
     * Driver-scoped listing — resolves the driver row for the calling user
     * and returns only that driver's active orders. If the caller has no
     * driver profile, returns an empty page (object-level auth fail-closed).
     */
    public Page<DispatchOrder> listDriverOrdersForUser(Long userId, int page, int size) {
        return driverRepo.findByUserId(userId)
                .map(d -> listDriverOrders(d.getId(), page, size))
                .orElseGet(() -> Page.empty(PageRequest.of(page, Math.min(size, 100))));
    }

    @Transactional
    public DispatchOrder grabOrder(Long orderId, Long userId) {
        DispatchOrder order = getOrder(orderId);
        if (!"PENDING".equals(order.getStatus())) {
            throw BusinessException.conflict(ErrorCode.ORDER_NOT_GRABBABLE, "Order is not grabbable");
        }

        Driver driver = driverRepo.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Driver profile not found"));

        validateDriverConstraints(driver, order);
        // Capacity policy applies to driver-initiated grab the same way it
        // applies to dispatcher assign — refuse when the zone is at cap.
        checkZoneCapacity(order.getZoneId());

        order.setAssignedDriverId(driver.getId());
        order.setStatus("ACCEPTED");
        orderRepo.save(order);

        recordAttempt(orderId, driver.getId(), false, "ACCEPTED", null);
        auditService.log(userId, "DRIVER", "ORDER_GRAB", "dispatch_order",
                orderId.toString(), null);
        return order;
    }

    @Transactional
    public DispatchOrder assignOrder(Long orderId, Long driverId, Long actorId) {
        DispatchOrder order = getOrder(orderId);
        checkZoneCapacity(order.getZoneId());

        order.setAssignedDriverId(driverId);
        order.setStatus("ASSIGNED");
        orderRepo.save(order);

        recordAttempt(orderId, driverId, false, "ASSIGNED", null);
        auditService.log(actorId, "DISPATCHER", "ORDER_ASSIGN", "dispatch_order",
                orderId.toString(), "driver=" + driverId);
        return order;
    }

    @Transactional
    public DispatchOrder forcedAssign(Long orderId, Long driverId, Long actorId) {
        DispatchOrder order = getOrder(orderId);

        // Check 30-minute anti-repeat
        Instant since = Instant.now().minus(FORCED_COOLDOWN_MINUTES, ChronoUnit.MINUTES);
        if (attemptRepo.existsForcedAttemptSince(orderId, driverId, since)) {
            throw BusinessException.conflict(ErrorCode.FORCED_REASSIGN_COOLDOWN,
                    "Cannot force-reassign same driver within 30 minutes");
        }

        // Capacity policy: forced assignment is still subject to zone capacity.
        // The override path lets a dispatcher pick *which* driver, but it does
        // not let the zone exceed its concurrent-assignment cap.
        checkZoneCapacity(order.getZoneId());

        order.setAssignedDriverId(driverId);
        order.setStatus("ASSIGNED");
        order.setForced(true);
        orderRepo.save(order);

        recordAttempt(orderId, driverId, true, "ASSIGNED", null);
        auditService.log(actorId, "DISPATCHER", "ORDER_FORCED_ASSIGN", "dispatch_order",
                orderId.toString(), "driver=" + driverId);
        return order;
    }

    @Transactional
    public DispatchOrder driverResponse(Long orderId, Long userId, boolean accept,
                                         RejectionReason rejectionReason) {
        DispatchOrder order = getOrder(orderId);
        Driver driver = driverRepo.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Driver not found"));

        if (!driver.getId().equals(order.getAssignedDriverId())) {
            throw BusinessException.forbidden("Not assigned to this order");
        }

        if (accept) {
            // Re-enforce eligibility on the accept path so a driver who has
            // dropped below the rating/distance/online thresholds since
            // assignment cannot accept the order.
            validateDriverConstraints(driver, order);
            order.setStatus("ACCEPTED");
            recordAttempt(orderId, driver.getId(), order.isForced(), "ACCEPTED", null);
        } else {
            if (order.isForced() && rejectionReason == null) {
                throw BusinessException.unprocessable(ErrorCode.REJECTION_REASON_REQUIRED,
                        "Rejection reason required for forced assignments");
            }
            String reasonStr = rejectionReason != null ? rejectionReason.name() : null;
            order.setStatus("PENDING");
            order.setAssignedDriverId(null);
            order.setForced(false);
            order.setRejectionReason(rejectionReason);
            recordAttempt(orderId, driver.getId(), order.isForced(), "REJECTED", reasonStr);
            auditService.log(userId, "DRIVER", "ORDER_REJECT", "dispatch_order",
                    orderId.toString(), "reason=" + reasonStr);
        }
        return orderRepo.save(order);
    }

    @Transactional
    public ZoneCapacityRule updateCapacity(Long zoneId, int maxConcurrent, Long actorId) {
        ZoneCapacityRule rule = capacityRepo.findByZoneId(zoneId).orElseGet(() -> {
            ZoneCapacityRule r = new ZoneCapacityRule();
            r.setZoneId(zoneId);
            return r;
        });
        rule.setMaxConcurrentAssignments(maxConcurrent);
        ZoneCapacityRule saved = capacityRepo.save(rule);
        auditService.log(actorId, "DISPATCHER", "ZONE_CAPACITY_UPDATE", "zone",
                zoneId.toString(), "max=" + maxConcurrent);
        return saved;
    }

    private DispatchOrder getOrder(Long orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Dispatch order not found"));
    }

    private void validateDriverConstraints(Driver driver, DispatchOrder order) {
        // Rating check
        if (driver.getRating().compareTo(MIN_RATING) < 0) {
            throw BusinessException.forbidden("Driver rating below minimum 4.2");
        }

        // Distance check
        if (order.getLatitude() != null && driver.getLatitude() != null) {
            double distance = calculateDistanceMiles(
                    driver.getLatitude().doubleValue(), driver.getLongitude().doubleValue(),
                    order.getLatitude().doubleValue(), order.getLongitude().doubleValue());
            if (distance > MAX_GRAB_DISTANCE_MILES.doubleValue()) {
                throw new BusinessException(ErrorCode.DRIVER_CONSTRAINT_FAILED,
                        "Driver too far from order (max 3 miles)",
                        org.springframework.http.HttpStatus.FORBIDDEN);
            }
        }

        // Online presence check
        var presence = presenceRepo.findByDriverIdAndPresenceDate(driver.getId(), clock.today());
        int minutes = presence.map(DriverDailyPresence::getMinutesOnline).orElse(0);
        if (minutes < MIN_ONLINE_MINUTES) {
            throw new BusinessException(ErrorCode.DRIVER_CONSTRAINT_FAILED,
                    "Driver must be online at least 15 minutes today",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }

    private void checkZoneCapacity(Long zoneId) {
        if (isZoneOverCapacity(zoneId)) {
            throw BusinessException.conflict(ErrorCode.ZONE_CAPACITY_EXCEEDED, "Zone capacity exceeded");
        }
    }

    /** Returns true when the zone has a capacity rule and is at/over it. */
    private boolean isZoneOverCapacity(Long zoneId) {
        if (zoneId == null) return false;
        ZoneCapacityRule rule = capacityRepo.findByZoneId(zoneId).orElse(null);
        if (rule == null) return false;
        return orderRepo.countActiveInZone(zoneId) >= rule.getMaxConcurrentAssignments();
    }

    private void recordAttempt(Long orderId, Long driverId, boolean forced, String status, String reason) {
        DispatchAttempt attempt = new DispatchAttempt();
        attempt.setOrderId(orderId);
        attempt.setDriverId(driverId);
        attempt.setForced(forced);
        attempt.setStatus(status);
        attempt.setRejectionReason(reason);
        attemptRepo.save(attempt);
    }

    private double calculateDistanceMiles(double lat1, double lon1, double lat2, double lon2) {
        double R = 3958.8; // Earth radius in miles
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
