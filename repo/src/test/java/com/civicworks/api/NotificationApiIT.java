package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationApiIT extends BaseApiIT {

    private String adminToken;
    private String userToken;
    private String editorToken;
    private UserEntity recipient;
    private Long templateId;
    private Long messageId;
    private Long reminderId;

    @BeforeAll
    void setup() {
        adminToken = login("admin", "admin123");
        recipient = createUser(unique("notif_user"), "pass123", Role.CONTENT_EDITOR);
        userToken = login(recipient.getUsername(), "pass123");
        editorToken = createUserAndLogin("notif_editor", "pass123", Role.CONTENT_EDITOR);
    }

    // ── POST /api/v1/notifications/templates ──

    @Test
    @Order(1)
    void createTemplate_asAdmin_returns201() {
        Map<String, Object> body = Map.of(
                "name", unique("tmpl"),
                "subject", "Test Subject",
                "body", "Hello {{name}}!",
                "channel", "IN_APP",
                "active", true
        );
        ResponseEntity<Map> resp = post("/api/v1/notifications/templates", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        templateId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createTemplate_missingBody_returns400() {
        Map<String, Object> body = Map.of("name", "test");
        ResponseEntity<Map> resp = post("/api/v1/notifications/templates", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createTemplate_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("name", "t", "body", "b");
        ResponseEntity<Map> resp = post("/api/v1/notifications/templates", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createTemplate_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/notifications/templates", Map.of("name", "t", "body", "b"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── PUT /api/v1/notifications/templates/{id} ──

    @Test
    @Order(2)
    void updateTemplate_asAdmin_returns200() {
        assertThat(templateId).isNotNull();
        Map<String, Object> body = Map.of("name", unique("tmpl_upd"), "body", "Updated body");
        ResponseEntity<Map> resp = put("/api/v1/notifications/templates/" + templateId, adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("body")).isEqualTo("Updated body");
    }

    @Test
    void updateTemplate_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("name", "x", "body", "y");
        ResponseEntity<Map> resp = put("/api/v1/notifications/templates/1", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/notifications/templates ──

    @Test
    @Order(3)
    void listTemplates_asAdmin_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/notifications/templates",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listTemplates_nonAdmin_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/notifications/templates", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/notifications/messages ──

    @Test
    @Order(4)
    void sendMessage_asAdmin_returns201() {
        Map<String, Object> body = Map.of(
                "recipientId", recipient.getId(),
                "subject", "Test Notification",
                "body", "You have a new message"
        );
        ResponseEntity<Map> resp = post("/api/v1/notifications/messages", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        messageId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void sendMessage_missingBody_returns400() {
        Map<String, Object> body = Map.of("recipientId", recipient.getId());
        ResponseEntity<Map> resp = post("/api/v1/notifications/messages", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sendMessage_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("recipientId", 1, "body", "test");
        ResponseEntity<Map> resp = post("/api/v1/notifications/messages", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/notifications/messages ──

    @Test
    @Order(5)
    void listMessages_asRecipient_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/notifications/messages",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(userToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listMessages_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/notifications/messages");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/notifications/messages/{id}/ack ──

    @Test
    @Order(6)
    void ackMessage_returns200() {
        assertThat(messageId).isNotNull();
        ResponseEntity<Map> resp = post("/api/v1/notifications/messages/" + messageId + "/ack",
                userToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("readAt")).isNotNull();
    }

    @Test
    void ackMessage_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/notifications/messages/1/ack", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/notifications/reminders ──

    @Test
    @Order(7)
    void createReminder_asAdmin_returns201() {
        Map<String, Object> body = Map.of(
                "recipientId", recipient.getId(),
                "entityType", "bill",
                "entityId", "123",
                "message", "Payment due soon",
                "scheduledAt", Instant.now().plusSeconds(3600).toString()
        );
        ResponseEntity<Map> resp = post("/api/v1/notifications/reminders", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        reminderId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createReminder_missingMessage_returns400() {
        Map<String, Object> body = Map.of("recipientId", 1, "scheduledAt", Instant.now().toString());
        ResponseEntity<Map> resp = post("/api/v1/notifications/reminders", adminToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReminder_nonAdmin_returns403() {
        Map<String, Object> body = Map.of("recipientId", 1, "message", "test",
                "scheduledAt", Instant.now().toString());
        ResponseEntity<Map> resp = post("/api/v1/notifications/reminders", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/notifications/reminders ──

    @Test
    @Order(8)
    void listReminders_asAdmin_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/notifications/reminders",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listReminders_nonAdmin_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/notifications/reminders", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/notifications/outbox ──

    @Test
    void listOutbox_asAdmin_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/notifications/outbox",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(adminToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listOutbox_nonAdmin_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/notifications/outbox", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/notifications/outbox/{id}/mark-exported ──

    @Test
    void markExported_nonAdmin_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/notifications/outbox/1/mark-exported", editorToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void markExported_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/notifications/outbox/1/mark-exported", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
