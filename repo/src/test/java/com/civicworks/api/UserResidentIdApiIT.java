package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserResidentIdApiIT extends BaseApiIT {

    private String adminToken;
    private String auditorToken;
    private String clerkToken;
    private String editorToken;
    private UserEntity targetUser;
    private final String RESIDENT_ID = "SSN-123-45-6789";

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        auditorToken = createUserAndLogin("urid_auditor", "pass123", Role.AUDITOR);
        clerkToken = createUserAndLogin("urid_clerk", "pass123", Role.BILLING_CLERK);
        editorToken = createUserAndLogin("urid_editor", "pass123", Role.CONTENT_EDITOR);
        targetUser = createUser(unique("urid_target"), "pass123", Role.DRIVER);
    }

    // ── POST /api/v1/users/{userId}/resident-id ──

    @Test
    @Order(1)
    void setResidentId_asAdmin_returns200() {
        Map<String, Object> body = Map.of("residentId", RESIDENT_ID);
        ResponseEntity<Map> resp = post("/api/v1/users/" + targetUser.getId() + "/resident-id",
                adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("userId")).isEqualTo(targetUser.getId().intValue());
        assertThat(resp.getBody().get("residentId")).isNotNull();
    }

    @Test
    void setResidentId_missingField_returns400() {
        ResponseEntity<Map> resp = post("/api/v1/users/" + targetUser.getId() + "/resident-id",
                adminToken, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setResidentId_nonAdmin_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/users/" + targetUser.getId() + "/resident-id",
                clerkToken, Map.of("residentId", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void setResidentId_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/users/" + targetUser.getId() + "/resident-id",
                Map.of("residentId", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/users/{userId}/resident-id ──

    @Test
    @Order(2)
    void getResidentId_asAdmin_seesPlaintext() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/" + targetUser.getId() + "/resident-id", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(false);
        assertThat(resp.getBody().get("residentId")).isEqualTo(RESIDENT_ID);
    }

    @Test
    @Order(2)
    void getResidentId_asAuditor_seesPlaintext() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/" + targetUser.getId() + "/resident-id", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(false);
    }

    @Test
    @Order(2)
    void getResidentId_asClerk_seesMasked() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/" + targetUser.getId() + "/resident-id", clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("masked")).isEqualTo(true);
        String maskedValue = (String) resp.getBody().get("residentId");
        assertThat(maskedValue).contains("*");
        assertThat(maskedValue).isNotEqualTo(RESIDENT_ID);
    }

    @Test
    void getResidentId_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/" + targetUser.getId() + "/resident-id", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/users/by-resident-id ──

    @Test
    @Order(3)
    void searchByResidentId_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/by-resident-id?q=" + RESIDENT_ID, adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("userId")).isEqualTo(targetUser.getId().intValue());
    }

    @Test
    @Order(3)
    void searchByResidentId_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/by-resident-id?q=" + RESIDENT_ID, auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchByResidentId_notFound_returns404() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/by-resident-id?q=NONEXISTENT", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchByResidentId_nonAuthorized_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/users/by-resident-id?q=test", clerkToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
