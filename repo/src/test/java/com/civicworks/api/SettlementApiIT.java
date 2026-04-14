package com.civicworks.api;

import com.civicworks.billing.domain.Account;
import com.civicworks.billing.domain.Bill;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import com.civicworks.settlement.domain.CashShift;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettlementApiIT extends BaseApiIT {

    private String adminToken;
    private String clerkToken;
    private String auditorToken;
    private String editorToken;
    private UserEntity clerkUser;
    private Account account;
    private Bill paymentBill;
    private CashShift shift;
    private Long paymentId;

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        clerkUser = createUser(unique("stl_clerk"), "pass123", Role.BILLING_CLERK);
        clerkToken = login(clerkUser.getUsername(), "pass123");
        auditorToken = createUserAndLogin("stl_auditor", "pass123", Role.AUDITOR);
        editorToken = createUserAndLogin("stl_editor", "pass123", Role.CONTENT_EDITOR);

        UserEntity owner = createUser(unique("stl_owner"), "pass123", Role.DRIVER);
        account = createAccount(owner.getId(), "Settlement Account");
        shift = createOpenShift(clerkUser.getId());
    }

    // ── POST /api/v1/settlements/payments ──

    @Test
    @Order(1)
    void postPayment_asClerk_returns201() {
        paymentBill = createBillWithLineItems(account.getId(), new BigDecimal("100.00"));
        Map<String, Object> body = Map.of(
                "billId", paymentBill.getId(),
                "amount", 100.00,
                "method", "CASH",
                "settlementType", "FULL"
        );
        ResponseEntity<Map> resp = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        paymentId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void postPayment_missingIdempotencyKey_returns400() {
        Bill b = createBillWithLineItems(account.getId(), new BigDecimal("50.00"));
        Map<String, Object> body = Map.of(
                "billId", b.getId(),
                "amount", 50.00,
                "method", "CASH",
                "settlementType", "FULL"
        );
        ResponseEntity<Map> resp = post("/api/v1/settlements/payments", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postPayment_invalidMethod_returns400() {
        Bill b = createBillWithLineItems(account.getId(), new BigDecimal("50.00"));
        Map<String, Object> body = Map.of(
                "billId", b.getId(),
                "amount", 50.00,
                "method", "BITCOIN",
                "settlementType", "FULL"
        );
        ResponseEntity<Map> resp = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postPayment_invalidSettlementType_returns400() {
        Bill b = createBillWithLineItems(account.getId(), new BigDecimal("50.00"));
        Map<String, Object> body = Map.of(
                "billId", b.getId(),
                "amount", 50.00,
                "method", "CASH",
                "settlementType", "PARTIAL"
        );
        ResponseEntity<Map> resp = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postPayment_nonClerk_returns403() {
        Map<String, Object> body = Map.of("billId", 1, "amount", 10, "method", "CASH", "settlementType", "FULL");
        ResponseEntity<Map> resp = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(editorToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postPayment_noAuth_returns401() {
        Map<String, Object> body = Map.of("billId", 1, "amount", 10, "method", "CASH", "settlementType", "FULL");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> resp = exchangeNoAuth("/api/v1/settlements/payments", HttpMethod.POST, body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void postPayment_idempotencyReplay() {
        Bill replayBill = createBillWithLineItems(account.getId(), new BigDecimal("75.00"));
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "billId", replayBill.getId(),
                "amount", 75.00,
                "method", "CHECK",
                "settlementType", "FULL"
        );

        ResponseEntity<Map> resp1 = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken, key)), Map.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Replay with same key and body should succeed
        ResponseEntity<Map> resp2 = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken, key)), Map.class);
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp2.getBody().get("id")).isEqualTo(resp1.getBody().get("id"));
    }

    @Test
    void postPayment_fullSettlement_amountMismatch_returns422() {
        Bill b = createBillWithLineItems(account.getId(), new BigDecimal("100.00"));
        Map<String, Object> body = Map.of(
                "billId", b.getId(),
                "amount", 50.00,
                "method", "CASH",
                "settlementType", "FULL"
        );
        ResponseEntity<Map> resp = rest.exchange("/api/v1/settlements/payments", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── POST /api/v1/settlements/payments/{paymentId}/reverse ──

    @Test
    @Order(2)
    void reversePayment_asClerk_returns200() {
        assertThat(paymentId).isNotNull();
        Map<String, Object> body = Map.of("reason", "Customer refund request");
        ResponseEntity<Map> resp = post("/api/v1/settlements/payments/" + paymentId + "/reverse",
                clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reversePayment_nonClerk_returns403() {
        Map<String, Object> body = Map.of("reason", "test");
        ResponseEntity<Map> resp = post("/api/v1/settlements/payments/1/reverse", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/settlements/shifts/{shiftId}/handover ──

    @Test
    @Order(3)
    void shiftHandover_asClerk_returns200() {
        ResponseEntity<Map> resp = post("/api/v1/settlements/shifts/" + shift.getId() + "/handover",
                clerkToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shiftHandover_asAdmin_returns200() {
        CashShift adminShift = createOpenShift(clerkUser.getId());
        ResponseEntity<Map> resp = post("/api/v1/settlements/shifts/" + adminShift.getId() + "/handover",
                adminToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shiftHandover_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/settlements/shifts/1/handover", editorToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/settlements/discrepancies ──

    @Test
    void listDiscrepancies_asClerk_returns200() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/settlements/discrepancies",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(clerkToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listDiscrepancies_asAuditor_returns200() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/settlements/discrepancies",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(auditorToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listDiscrepancies_asAdmin_returns200() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/settlements/discrepancies",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listDiscrepancies_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/settlements/discrepancies", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listDiscrepancies_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/settlements/discrepancies");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/settlements/discrepancies/{id}/resolve ──

    @Test
    void resolveDiscrepancy_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("notes", "Resolved");
        ResponseEntity<Map> resp = post("/api/v1/settlements/discrepancies/1/resolve", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolveDiscrepancy_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/settlements/discrepancies/1/resolve",
                Map.of("notes", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
