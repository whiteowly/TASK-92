package com.civicworks.api;

import com.civicworks.dispatch.domain.Driver;
import com.civicworks.dispatch.domain.Zone;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DispatchApiIT extends BaseApiIT {

    private String dispatcherToken;
    private String driverToken;
    private String editorToken;
    private UserEntity driverUser;
    private Driver driver;
    private Zone zone;
    private Long orderId;

    @BeforeAll
    void setup() {
        UserEntity dispatcherUser = createUser(unique("dsp_dispatcher"), "pass123", Role.DISPATCHER);
        dispatcherToken = login(dispatcherUser.getUsername(), "pass123");

        driverUser = createUser(unique("dsp_driver"), "pass123", Role.DRIVER);
        driverToken = login(driverUser.getUsername(), "pass123");

        editorToken = createUserAndLogin("dsp_editor", "pass123", Role.CONTENT_EDITOR);

        // Setup zone, driver, presence, capacity
        zone = createZone(unique("zone"));
        driver = createDriver(driverUser.getId(), new BigDecimal("4.50"),
                new BigDecimal("40.7128"), new BigDecimal("-74.0060"));
        createPresence(driver.getId(), 60); // 60 minutes online
        createCapacityRule(zone.getId(), 10);
    }

    // ── POST /api/v1/dispatch/orders ──

    @Test
    @Order(1)
    void createOrder_asDispatcher_returns201() {
        Map<String, Object> body = Map.of(
                "zoneId", zone.getId(),
                "description", "Pickup at Main St",
                "priority", 1,
                "latitude", 40.7128,
                "longitude", -74.0060
        );
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders", dispatcherToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("status")).isEqualTo("PENDING");
        orderId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createOrder_nonDispatcher_returns403() {
        Map<String, Object> body = Map.of("zoneId", 1, "description", "test");
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders", driverToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createOrder_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/dispatch/orders", Map.of("description", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/dispatch/orders ──

    @Test
    @Order(2)
    void listOrders_asDispatcher_seesAll() {
        ResponseEntity<Map> resp = getMap("/api/v1/dispatch/orders?page=0&size=20", dispatcherToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    @Order(2)
    void listOrders_asDriver_seesOwnOnly() {
        ResponseEntity<Map> resp = getMap("/api/v1/dispatch/orders?page=0&size=20", driverToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listOrders_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/dispatch/orders", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listOrders_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/dispatch/orders");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/dispatch/orders/{orderId}/grab ──

    @Test
    @Order(3)
    void grabOrder_asDriver_returns200() {
        assertThat(orderId).isNotNull();
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/" + orderId + "/grab", driverToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isIn("ASSIGNED", "ACCEPTED");
    }

    @Test
    void grabOrder_nonDriver_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/1/grab", dispatcherToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/dispatch/orders/{orderId}/driver-response ──

    @Test
    @Order(4)
    void driverResponse_accept_returns200() {
        assertThat(orderId).isNotNull();
        Map<String, Object> body = Map.of("accept", true);
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/" + orderId + "/driver-response",
                driverToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void driverResponse_nonDriver_returns403() {
        Map<String, Object> body = Map.of("accept", true);
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/1/driver-response", dispatcherToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/dispatch/orders/{orderId}/assign ──

    @Test
    @Order(5)
    void assignOrder_asDispatcher_returns200() {
        // Create a new order for assignment
        Map<String, Object> orderBody = Map.of(
                "zoneId", zone.getId(),
                "description", "Assign test",
                "latitude", 40.7128,
                "longitude", -74.0060
        );
        ResponseEntity<Map> created = post("/api/v1/dispatch/orders", dispatcherToken, orderBody);
        Long newOrderId = ((Number) created.getBody().get("id")).longValue();

        Map<String, Object> body = Map.of("driverId", driver.getId());
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/" + newOrderId + "/assign",
                dispatcherToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void assignOrder_nonDispatcher_returns403() {
        Map<String, Object> body = Map.of("driverId", 1);
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/1/assign", driverToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/dispatch/orders/{orderId}/forced-assign ──

    @Test
    @Order(6)
    void forcedAssign_asDispatcher_returns200() {
        // Create a new order
        Map<String, Object> orderBody = Map.of(
                "zoneId", zone.getId(),
                "description", "Forced assign test",
                "latitude", 40.7128,
                "longitude", -74.0060
        );
        ResponseEntity<Map> created = post("/api/v1/dispatch/orders", dispatcherToken, orderBody);
        Long newOrderId = ((Number) created.getBody().get("id")).longValue();

        Map<String, Object> body = Map.of("driverId", driver.getId());
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/" + newOrderId + "/forced-assign",
                dispatcherToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("forced")).isEqualTo(true);
    }

    @Test
    void forcedAssign_nonDispatcher_returns403() {
        Map<String, Object> body = Map.of("driverId", 1);
        ResponseEntity<Map> resp = post("/api/v1/dispatch/orders/1/forced-assign", driverToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PUT /api/v1/dispatch/zones/{zoneId}/capacity-rule ──

    @Test
    @Order(7)
    void updateCapacityRule_asDispatcher_returns200() {
        Map<String, Object> body = Map.of("maxConcurrentAssignments", 5);
        ResponseEntity<Map> resp = put("/api/v1/dispatch/zones/" + zone.getId() + "/capacity-rule",
                dispatcherToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("maxConcurrentAssignments")).isEqualTo(5);
    }

    @Test
    void updateCapacityRule_nonDispatcher_returns403() {
        Map<String, Object> body = Map.of("maxConcurrentAssignments", 5);
        ResponseEntity<Map> resp = put("/api/v1/dispatch/zones/1/capacity-rule", driverToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateCapacityRule_noAuth_returns401() {
        ResponseEntity<Map> resp = exchangeNoAuth("/api/v1/dispatch/zones/1/capacity-rule",
                HttpMethod.PUT, Map.of("maxConcurrentAssignments", 5), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
