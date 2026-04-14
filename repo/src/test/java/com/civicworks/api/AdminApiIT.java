package com.civicworks.api;

import com.civicworks.platform.security.Role;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminApiIT extends BaseApiIT {

    private String adminToken;
    private String auditorToken;
    private String editorToken;

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        auditorToken = createUserAndLogin("admin_auditor", "pass123", Role.AUDITOR);
        editorToken = createUserAndLogin("admin_editor", "pass123", Role.CONTENT_EDITOR);
    }

    // ── GET /api/v1/admin/system-config ──

    @Test
    void getSystemConfig_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/system-config", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("timezone");
        assertThat(resp.getBody()).containsKey("sessionTtlMinutes");
    }

    @Test
    void getSystemConfig_asEditor_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/system-config", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getSystemConfig_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/admin/system-config");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── PUT /api/v1/admin/system-config ──

    @Test
    void updateSystemConfig_asAdmin_returns200() {
        Map<String, Object> body = Map.of(
                "timezone", "America/Chicago",
                "sessionTtlMinutes", 120,
                "searchHistoryRetentionDays", 60
        );
        ResponseEntity<Map> resp = put("/api/v1/admin/system-config", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("timezone")).isEqualTo("America/Chicago");

        // Restore
        put("/api/v1/admin/system-config", adminToken, Map.of("timezone", "America/New_York",
                "sessionTtlMinutes", 480, "searchHistoryRetentionDays", 90));
    }

    @Test
    void updateSystemConfig_invalidTimezone_returns500() {
        // ZoneId.of() throws DateTimeException for invalid zones, caught as generic exception
        Map<String, Object> body = Map.of("timezone", "Invalid/Zone");
        ResponseEntity<Map> resp = put("/api/v1/admin/system-config", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateSystemConfig_sessionTtlOutOfRange_returns400() {
        // @Min(60) on sessionTtlMinutes triggers MethodArgumentNotValidException
        Map<String, Object> body = Map.of("sessionTtlMinutes", 10);
        ResponseEntity<Map> resp = put("/api/v1/admin/system-config", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateSystemConfig_nonAdmin_returns403() {
        ResponseEntity<Map> resp = put("/api/v1/admin/system-config", auditorToken, Map.of("timezone", "UTC"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/admin/audit-log ──

    @Test
    void getAuditLog_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/audit-log", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void getAuditLog_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/audit-log", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLog_withFilters() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/audit-log?action=LOGIN&page=0&size=10", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAuditLog_asEditor_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/admin/audit-log", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getAuditLog_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/admin/audit-log");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
