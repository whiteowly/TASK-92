package com.civicworks.api;

import com.civicworks.billing.domain.Account;
import com.civicworks.billing.domain.Bill;
import com.civicworks.billing.domain.FeeItem;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingApiIT extends BaseApiIT {

    private String adminToken;
    private String clerkToken;
    private String auditorToken;
    private String editorToken;
    private Long feeItemId;
    private Account account;
    private Bill bill;

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        clerkToken = createUserAndLogin("bill_clerk", "pass123", Role.BILLING_CLERK);
        auditorToken = createUserAndLogin("bill_auditor", "pass123", Role.AUDITOR);
        editorToken = createUserAndLogin("bill_editor", "pass123", Role.CONTENT_EDITOR);

        UserEntity owner = createUser(unique("bill_owner"), "pass123", Role.DRIVER);
        account = createAccount(owner.getId(), "Billing Test Account");

        // Create a fee item for billing runs
        FeeItem fee = createFeeItem(unique("WATER"), "Water Service", FeeItem.CalculationType.FLAT, new BigDecimal("25.00"));

        bill = createBillWithLineItems(account.getId(), new BigDecimal("150.00"));
    }

    // ── POST /api/v1/billing/fee-items ──

    @Test
    @Order(1)
    void createFeeItem_asClerk_returns201() {
        Map<String, Object> body = Map.of(
                "code", unique("FEE"),
                "name", "Water Service Fee",
                "calculationType", "FLAT",
                "rate", 25.50,
                "active", true,
                "taxableFlag", false
        );
        ResponseEntity<Map> resp = post("/api/v1/billing/fee-items", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("name")).isEqualTo("Water Service Fee");
        feeItemId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createFeeItem_missingCode_returns400() {
        Map<String, Object> body = Map.of("name", "Test", "calculationType", "FLAT", "rate", 10);
        ResponseEntity<Map> resp = post("/api/v1/billing/fee-items", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createFeeItem_invalidCalcType_returns400() {
        Map<String, Object> body = Map.of("code", "X", "name", "X", "calculationType", "INVALID", "rate", 10);
        ResponseEntity<Map> resp = post("/api/v1/billing/fee-items", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createFeeItem_nonClerk_returns403() {
        Map<String, Object> body = Map.of("code", "X", "name", "X", "calculationType", "FLAT", "rate", 10);
        ResponseEntity<Map> resp = post("/api/v1/billing/fee-items", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createFeeItem_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/billing/fee-items",
                Map.of("code", "X", "name", "X", "calculationType", "FLAT", "rate", 10));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── PUT /api/v1/billing/fee-items/{id} ──

    @Test
    @Order(2)
    void updateFeeItem_asClerk_returns200() {
        assertThat(feeItemId).isNotNull();
        Map<String, Object> body = Map.of(
                "code", unique("FEE_UPD"),
                "name", "Updated Fee",
                "calculationType", "PER_UNIT",
                "rate", 30.00,
                "taxableFlag", true
        );
        ResponseEntity<Map> resp = put("/api/v1/billing/fee-items/" + feeItemId, clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("name")).isEqualTo("Updated Fee");
        assertThat(resp.getBody().get("taxableFlag")).isEqualTo(true);
    }

    @Test
    void updateFeeItem_nonClerk_returns403() {
        Map<String, Object> body = Map.of("code", "X", "name", "X", "calculationType", "FLAT", "rate", 10);
        ResponseEntity<Map> resp = put("/api/v1/billing/fee-items/1", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/billing/fee-items ──

    @Test
    @Order(3)
    void listFeeItems_asClerk_returns200() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/billing/fee-items",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(clerkToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("code");
    }

    @Test
    void listFeeItems_nonClerk_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/fee-items", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/billing/policies/due-date ──

    @Test
    void getDueDatePolicy_asClerk_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/policies/due-date", clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getDueDatePolicy_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/policies/due-date", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getDueDatePolicy_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/policies/due-date", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getDueDatePolicy_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/policies/due-date", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PUT /api/v1/billing/policies/due-date ──

    @Test
    void updateDueDatePolicy_asAdmin_returns200() {
        Map<String, Object> body = Map.of(
                "monthlyDueInDays", 20,
                "quarterlyDueInDays", 30,
                "effectiveFrom", "2025-01-01T00:00:00Z"
        );
        ResponseEntity<Map> resp = put("/api/v1/billing/policies/due-date", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateDueDatePolicy_outOfRange_returns400() {
        Map<String, Object> body = Map.of("monthlyDueInDays", 0, "quarterlyDueInDays", 100);
        ResponseEntity<Map> resp = put("/api/v1/billing/policies/due-date", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDueDatePolicy_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("monthlyDueInDays", 15, "quarterlyDueInDays", 15);
        ResponseEntity<Map> resp = put("/api/v1/billing/policies/due-date", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/billing/runs ──

    @Test
    void createBillingRun_asClerk_returns201() {
        Map<String, Object> body = Map.of(
                "cycleDate", LocalDate.now().toString(),
                "cycleType", "MONTHLY"
        );
        ResponseEntity<Map> resp = rest.exchange("/api/v1/billing/runs", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("status")).isIn("RUNNING", "COMPLETED");
    }

    @Test
    void createBillingRun_missingIdempotencyKey_returns400() {
        Map<String, Object> body = Map.of("cycleDate", "2025-01-01", "cycleType", "MONTHLY");
        ResponseEntity<Map> resp = post("/api/v1/billing/runs", clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createBillingRun_nonClerk_returns403() {
        Map<String, Object> body = Map.of("cycleDate", "2025-01-01", "cycleType", "MONTHLY");
        ResponseEntity<Map> resp = rest.exchange("/api/v1/billing/runs", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(editorToken)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createBillingRun_idempotencyReplay() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "cycleDate", LocalDate.now().plusDays(1).toString(),
                "cycleType", "QUARTERLY"
        );
        HttpHeaders h = idempotentHeaders(clerkToken, key);

        ResponseEntity<Map> resp1 = rest.exchange("/api/v1/billing/runs", HttpMethod.POST,
                new HttpEntity<>(body, h), Map.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Replay with same key and body should return same result
        ResponseEntity<Map> resp2 = rest.exchange("/api/v1/billing/runs", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken, key)), Map.class);
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /api/v1/billing/runs/{runId} ──

    @Test
    void getBillingRun_asClerk_returns200() {
        // Create a run first
        Map<String, Object> body = Map.of("cycleDate", LocalDate.now().plusDays(10).toString(), "cycleType", "MONTHLY");
        ResponseEntity<Map> created = rest.exchange("/api/v1/billing/runs", HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(clerkToken)), Map.class);
        Long runId = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> resp = getMap("/api/v1/billing/runs/" + runId, clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getBillingRun_asAuditor_allowed() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/runs/1", auditorToken);
        // May be 200 or 404 depending on data, but not 403
        assertThat(resp.getStatusCode().value()).isIn(200, 404);
    }

    @Test
    void getBillingRun_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/runs/1", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/billing/bills ──

    @Test
    void listBills_asClerk_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills?page=0&size=10", clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void listBills_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listBills_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/billing/bills/{billId} ──

    @Test
    void getBill_asClerk_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills/" + bill.getId(), clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("id")).longValue()).isEqualTo(bill.getId());
    }

    @Test
    void getBill_notFound_returns404() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills/999999", clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getBill_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/bills/" + bill.getId(), editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/billing/bills/{billId}/discounts ──

    @Test
    void applyDiscount_asClerk_returns201() {
        Bill discountBill = createBillWithLineItems(account.getId(), new BigDecimal("200.00"));
        Map<String, Object> body = Map.of("discountType", "PERCENTAGE", "value", 10);
        ResponseEntity<Map> resp = post("/api/v1/billing/bills/" + discountBill.getId() + "/discounts",
                clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void applyDiscount_missingFields_returns400() {
        ResponseEntity<Map> resp = post("/api/v1/billing/bills/" + bill.getId() + "/discounts",
                clerkToken, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void applyDiscount_nonClerk_returns403() {
        Map<String, Object> body = Map.of("discountType", "FIXED", "value", 10);
        ResponseEntity<Map> resp = post("/api/v1/billing/bills/1/discounts", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
