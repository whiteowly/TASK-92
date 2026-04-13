package com.civicworks.dispatch.api;

import com.civicworks.dispatch.application.DispatchService;
import com.civicworks.dispatch.domain.DispatchOrder;
import com.civicworks.dispatch.domain.RejectionReason;
import com.civicworks.dispatch.domain.ZoneCapacityRule;
import com.civicworks.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<DispatchOrder> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        DispatchOrder order = new DispatchOrder();
        order.setZoneId(request.zoneId());
        order.setDescription(request.description());
        order.setPriority(request.priority() != null ? request.priority() : 0);
        order.setLatitude(request.latitude());
        order.setLongitude(request.longitude());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dispatchService.createOrder(order, SecurityUtils.currentUserId()));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'DRIVER')")
    public ResponseEntity<Page<DispatchOrder>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Object-level authorization: DRIVER callers see only their own
        // assigned orders, never the global queue. DISPATCHER (and any role
        // that holds DISPATCHER alongside DRIVER) keeps the global view.
        var principal = SecurityUtils.currentPrincipal();
        boolean isDispatcher = principal != null
                && principal.hasRole(com.civicworks.platform.security.Role.DISPATCHER);
        if (!isDispatcher
                && principal != null
                && principal.hasRole(com.civicworks.platform.security.Role.DRIVER)) {
            return ResponseEntity.ok(
                    dispatchService.listDriverOrdersForUser(principal.userId(), page, size));
        }
        return ResponseEntity.ok(dispatchService.listOrders(page, size));
    }

    @PostMapping("/orders/{orderId}/grab")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DispatchOrder> grabOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(dispatchService.grabOrder(orderId, SecurityUtils.currentUserId()));
    }

    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<DispatchOrder> assignOrder(@PathVariable Long orderId,
                                                      @RequestBody AssignRequest request) {
        return ResponseEntity.ok(dispatchService.assignOrder(
                orderId, request.driverId(), SecurityUtils.currentUserId()));
    }

    @PostMapping("/orders/{orderId}/forced-assign")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<DispatchOrder> forcedAssign(@PathVariable Long orderId,
                                                       @RequestBody ForcedAssignRequest request) {
        return ResponseEntity.ok(dispatchService.forcedAssign(
                orderId, request.driverId(), SecurityUtils.currentUserId()));
    }

    @PostMapping("/orders/{orderId}/driver-response")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DispatchOrder> driverResponse(@PathVariable Long orderId,
                                                         @RequestBody DriverResponseRequest request) {
        return ResponseEntity.ok(dispatchService.driverResponse(
                orderId, SecurityUtils.currentUserId(), request.accept(), request.rejectionReason()));
    }

    @PutMapping("/zones/{zoneId}/capacity-rule")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<ZoneCapacityRule> updateCapacity(@PathVariable Long zoneId,
                                                            @RequestBody CapacityRequest request) {
        return ResponseEntity.ok(dispatchService.updateCapacity(
                zoneId, request.maxConcurrentAssignments(), SecurityUtils.currentUserId()));
    }

    public record CreateOrderRequest(Long zoneId, String description, Integer priority,
                                      BigDecimal latitude, BigDecimal longitude) {}
    public record AssignRequest(@NotNull Long driverId) {}
    public record ForcedAssignRequest(@NotNull Long driverId) {}
    public record DriverResponseRequest(boolean accept, RejectionReason rejectionReason) {}
    public record CapacityRequest(int maxConcurrentAssignments) {}
}
