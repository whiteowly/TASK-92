package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthApiIT extends BaseApiIT {

    private String adminToken;
    private UserEntity testUser;

    @BeforeAll
    void setup() {
        testUser = createUser(unique("auth_user"), "pass123", Role.CONTENT_EDITOR);
        adminToken = login("admin", "admin123");
    }

    // ── POST /api/v1/auth/login ──

    @Test
    void login_success() {
        Map<String, Object> body = Map.of("username", testUser.getUsername(), "password", "pass123");
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("accessToken")).asString().startsWith("cwk_sess_");
        assertThat(resp.getBody().get("tokenType")).isEqualTo("Bearer");
        assertThat(resp.getBody().get("roles")).asList().contains("CONTENT_EDITOR");
    }

    @Test
    void login_badPassword_returns401() {
        Map<String, Object> body = Map.of("username", testUser.getUsername(), "password", "wrong");
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_unknownUser_returns401() {
        Map<String, Object> body = Map.of("username", "noexist", "password", "pass");
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_disabledUser_returns423() {
        UserEntity disabled = createUser(unique("disabled"), "pass123", Role.CONTENT_EDITOR);
        disabled.setStatus(UserEntity.UserStatus.DISABLED);
        userRepository.saveAndFlush(disabled);

        Map<String, Object> body = Map.of("username", disabled.getUsername(), "password", "pass123");
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", body);
        assertThat(resp.getStatusCode().value()).isEqualTo(423);
    }

    @Test
    void login_missingFields_returns400() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── POST /api/v1/auth/logout ──

    @Test
    void logout_success() {
        String token = login(testUser.getUsername(), "pass123");
        HttpHeaders h = bearerHeaders(token);
        ResponseEntity<Void> resp = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(h), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Token should now be invalid
        ResponseEntity<Map> check = getMap("/api/v1/search/history", token);
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_noToken_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/logout", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/auth/sessions ──

    @Test
    void sessions_asAdmin_returns200() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/auth/sessions",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("sessionId");
    }

    @Test
    void sessions_filterByUserId() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/auth/sessions?userId=" + testUser.getId(),
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sessions_nonAdmin_returns403() {
        String token = login(testUser.getUsername(), "pass123");
        ResponseEntity<Map> resp = getMap("/api/v1/auth/sessions", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sessions_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/auth/sessions");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/auth/sessions/{sessionId}/revoke ──

    @Test
    void revokeSession_asAdmin_returns200() {
        // Create a session to revoke
        String token = login(testUser.getUsername(), "pass123");
        ResponseEntity<String> sessions = rest.exchange(
                "/api/v1/auth/sessions?userId=" + testUser.getId(),
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        // Parse session ID from response - find an active session
        String body = sessions.getBody();
        // Use a simple regex approach to find a sessionId where revokedAt is null
        assertThat(body).contains("sessionId");
        // Extract first sessionId from the JSON array
        int idx = body.indexOf("\"sessionId\":");
        assertThat(idx).isGreaterThan(-1);
        String sub = body.substring(idx + 12);
        String idStr = sub.substring(0, sub.indexOf(",")).trim();
        Long sessionId = Long.parseLong(idStr);

        ResponseEntity<Void> resp = rest.exchange("/api/v1/auth/sessions/" + sessionId + "/revoke",
                HttpMethod.POST, new HttpEntity<>(bearerHeaders(adminToken)), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void revokeSession_nonAdmin_returns403() {
        String token = login(testUser.getUsername(), "pass123");
        ResponseEntity<Map> resp = post("/api/v1/auth/sessions/1/revoke", token, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
