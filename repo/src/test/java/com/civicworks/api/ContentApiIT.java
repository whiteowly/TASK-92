package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentApiIT extends BaseApiIT {

    private String editorToken;
    private String adminToken;
    private String auditorToken;
    private String noRoleToken;
    private Long createdContentId;

    @BeforeAll
    void setup() {
        editorToken = createUserAndLogin("cnt_editor", "pass123", Role.CONTENT_EDITOR);
        adminToken = login("admin", "admin123");
        auditorToken = createUserAndLogin("cnt_auditor", "pass123", Role.AUDITOR);
        noRoleToken = createUserAndLogin("cnt_norole", "pass123", Role.DRIVER);
    }

    // ── POST /api/v1/content-items ──

    @Test
    @Order(1)
    void createContent_asEditor_returns201() {
        Map<String, Object> body = Map.of(
                "title", "Test Article",
                "body", "<p>Hello <script>alert('xss')</script></p>",
                "contentType", "NEWS",
                "origin", "test",
                "price", 9.99,
                "tags", Set.of("general", "test")
        );
        ResponseEntity<Map> resp = post("/api/v1/content-items", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("title")).isEqualTo("Test Article");
        assertThat(resp.getBody().get("state")).isEqualTo("DRAFT");
        // Script tag should be sanitized
        assertThat((String) resp.getBody().get("sanitizedBody")).doesNotContain("<script>");
        createdContentId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createContent_missingTitle_returns400() {
        Map<String, Object> body = Map.of("contentType", "NEWS");
        ResponseEntity<Map> resp = post("/api/v1/content-items", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createContent_invalidContentType_returns400() {
        Map<String, Object> body = Map.of("title", "Test", "contentType", "INVALID");
        ResponseEntity<Map> resp = post("/api/v1/content-items", editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createContent_nonEditor_returns403() {
        Map<String, Object> body = Map.of("title", "Test", "contentType", "NEWS");
        ResponseEntity<Map> resp = post("/api/v1/content-items", noRoleToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createContent_noAuth_returns401() {
        ResponseEntity<Map> resp = postNoAuth("/api/v1/content-items", Map.of("title", "T", "contentType", "NEWS"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/content-items ──

    @Test
    @Order(2)
    void listContent_asEditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void listContent_withStateFilter() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items?state=DRAFT&page=0&size=5", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listContent_asAdmin_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listContent_asAuditor_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items", auditorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/content-items/{id} ──

    @Test
    @Order(3)
    void getContent_asEditor_returns200() {
        assertThat(createdContentId).isNotNull();
        ResponseEntity<Map> resp = getMap("/api/v1/content-items/" + createdContentId, editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("id")).isEqualTo(createdContentId.intValue());
    }

    @Test
    void getContent_notFound_returns404() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items/999999", editorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /api/v1/content-items/{id} ──

    @Test
    @Order(4)
    void updateContent_asEditor_returns200() {
        assertThat(createdContentId).isNotNull();
        Map<String, Object> body = Map.of("title", "Updated Title", "contentType", "NEWS");
        ResponseEntity<Map> resp = put("/api/v1/content-items/" + createdContentId, editorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("title")).isEqualTo("Updated Title");
    }

    @Test
    void updateContent_nonEditor_returns403() {
        Map<String, Object> body = Map.of("title", "Nope", "contentType", "NEWS");
        ResponseEntity<Map> resp = put("/api/v1/content-items/1", noRoleToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/content-items/{id}/publish ──

    @Test
    @Order(5)
    void publishContent_asEditor_returns200() {
        assertThat(createdContentId).isNotNull();
        ResponseEntity<Map> resp = post("/api/v1/content-items/" + createdContentId + "/publish", editorToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("state")).isEqualTo("PUBLISHED");
        assertThat(resp.getBody().get("publishedAt")).isNotNull();
    }

    @Test
    void publishContent_nonEditor_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/content-items/1/publish", noRoleToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/content-items/{id}/unpublish ──

    @Test
    @Order(6)
    void unpublishContent_asEditor_returns200() {
        assertThat(createdContentId).isNotNull();
        ResponseEntity<Map> resp = post("/api/v1/content-items/" + createdContentId + "/unpublish", editorToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("state")).isEqualTo("UNPUBLISHED");
    }

    // ── GET /api/v1/content-items/{id}/publish-history ──

    @Test
    @Order(7)
    void publishHistory_asEditor_returns200() {
        assertThat(createdContentId).isNotNull();
        ResponseEntity<Object[]> resp = rest.exchange(
                "/api/v1/content-items/" + createdContentId + "/publish-history",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(editorToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().length).isGreaterThanOrEqualTo(2); // publish + unpublish
    }

    @Test
    void publishHistory_nonEditor_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/content-items/1/publish-history", noRoleToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/public/content-items ──

    @Test
    void publicListContent_noAuth_returns200() {
        // First publish something so there's data
        UserEntity editor = createUser(unique("pub_editor"), "pass123", Role.CONTENT_EDITOR);
        createPublishedContent(editor.getId());

        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/content-items");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void publicListContent_withFilters() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/content-items?type=NEWS&page=0&size=5");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/public/content-items/{id} ──

    @Test
    void publicGetContent_published_returns200() {
        UserEntity editor = createUser(unique("pub_editor2"), "pass123", Role.CONTENT_EDITOR);
        var content = createPublishedContent(editor.getId());
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/content-items/" + content.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("title")).isNotNull();
    }

    @Test
    void publicGetContent_notFound_returns404() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/content-items/999999");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
