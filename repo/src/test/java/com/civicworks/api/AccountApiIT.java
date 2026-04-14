package com.civicworks.api;

import com.civicworks.billing.domain.Account;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountApiIT extends BaseApiIT {

    private String adminToken;
    private String clerkToken;
    private String auditorToken;
    private String editorToken;
    private Account account;
    private final String RESIDENT_ID = "ACC-RES-987-65-4321";

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        clerkToken = createUserAndLogin("acc_clerk", "pass123", Role.BILLING_CLERK);
        auditorToken = createUserAndLogin("acc_auditor", "pass123", Role.AUDITOR);
        editorToken = createUserAndLogin("acc_editor", "pass123", Role.CONTENT_EDITOR);

        UserEntity owner = createUser(unique("acc_owner"), "pass123", Role.DRIVER);
        account = createAccount(owner.getId(), "Test Account");
    }

    // ── POST /api/v1/billing/accounts/{accountId}/resident-id ──

    @Test
    @Order(1)
    void setResidentId_asAdmin_returns200() {
        Map<String, Object> body = Map.of("residentId", RESIDENT_ID);
        ResponseEntity<Map> resp = post("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("accountId")).isEqualTo(account.getId().intValue());
    }

    @Test
    @Order(1)
    void setResidentId_asClerk_returns200() {
        Account a2 = createAccount(account.getUserId(), "Clerk Account");
        Map<String, Object> body = Map.of("residentId", "CLERK-RES-111");
        ResponseEntity<Map> resp = post("/api/v1/billing/accounts/" + a2.getId() + "/resident-id",
                clerkToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void setResidentId_missingField_returns400() {
        ResponseEntity<Map> resp = post("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                adminToken, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setResidentId_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                editorToken, Map.of("residentId", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void setResidentId_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                Map.of("residentId", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/billing/accounts/{accountId}/resident-id ──

    @Test
    @Order(2)
    void getResidentId_asAdmin_seesPlaintext() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(false);
        assertThat(resp.getBody().get("residentId")).isEqualTo(RESIDENT_ID);
    }

    @Test
    @Order(2)
    void getResidentId_asAuditor_seesPlaintext() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(false);
    }

    @Test
    @Order(2)
    void getResidentId_asClerk_seesMasked() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(true);
        String masked = (String) resp.getBody().get("residentId");
        assertThat(masked).contains("*");
    }

    @Test
    void getResidentId_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/" + account.getId() + "/resident-id",
                editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/billing/accounts/by-resident-id ──

    @Test
    @Order(3)
    void searchByResidentId_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/by-resident-id?q=" + RESIDENT_ID,
                adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("accountId")).isEqualTo(account.getId().intValue());
    }

    @Test
    @Order(3)
    void searchByResidentId_asClerk_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/by-resident-id?q=" + RESIDENT_ID,
                clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchByResidentId_notFound_returns404() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/by-resident-id?q=NONEXISTENT", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchByResidentId_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/billing/accounts/by-resident-id?q=test", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
