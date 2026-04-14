package com.civicworks.api;

import com.civicworks.content.domain.ContentItem;
import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModerationApiIT extends BaseApiIT {

    private String moderatorToken;
    private String editorToken;
    private String noRoleToken;
    private UserEntity commentAuthor;
    private ContentItem publishedContent;
    private Long commentId;
    private Long sensitiveWordId;

    @BeforeAll
    void setup() {
        moderatorToken = createUserAndLogin("mod_mod", "pass123", Role.MODERATOR);
        editorToken = createUserAndLogin("mod_editor", "pass123", Role.CONTENT_EDITOR);
        commentAuthor = createUser(unique("mod_author"), "pass123", Role.CONTENT_EDITOR);
        publishedContent = createPublishedContent(commentAuthor.getId());
        noRoleToken = createUserAndLogin("mod_norole", "pass123", Role.DRIVER);
    }

    // ── POST /api/v1/content-items/{contentItemId}/comments ──

    @Test
    @Order(1)
    void createComment_onPublished_returns201() {
        String authorToken = login(commentAuthor.getUsername(), "pass123");
        Map<String, Object> body = Map.of("body", "Great article!");
        ResponseEntity<Map> resp = post("/api/v1/content-items/" + publishedContent.getId() + "/comments",
                authorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("body")).isEqualTo("Great article!");
        commentId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createComment_missingBody_returns400() {
        String authorToken = login(commentAuthor.getUsername(), "pass123");
        ResponseEntity<Map> resp = post("/api/v1/content-items/" + publishedContent.getId() + "/comments",
                authorToken, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createComment_nonExistentContent_returns404() {
        String authorToken = login(commentAuthor.getUsername(), "pass123");
        ResponseEntity<Map> resp = post("/api/v1/content-items/999999/comments",
                authorToken, Map.of("body", "Test"));
        assertThat(resp.getStatusCode().value()).isIn(404, 422);
    }

    // ── GET /api/v1/moderation/comments ──

    @Test
    @Order(2)
    void listComments_asModerator_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/moderation/comments", moderatorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void listComments_withStateFilter() {
        ResponseEntity<Map> resp = getMap("/api/v1/moderation/comments?state=PENDING&page=0&size=5", moderatorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listComments_nonModerator_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/moderation/comments", noRoleToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listComments_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/moderation/comments");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/moderation/comments/{commentId}/actions ──

    @Test
    @Order(3)
    void moderateComment_approve_returns200() {
        assertThat(commentId).isNotNull();
        Map<String, Object> body = Map.of("action", "APPROVE", "reason", "Looks good");
        ResponseEntity<Map> resp = post("/api/v1/moderation/comments/" + commentId + "/actions",
                moderatorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void moderateComment_missingFields_returns400() {
        Map<String, Object> body = Map.of("action", "APPROVE");
        ResponseEntity<Map> resp = post("/api/v1/moderation/comments/1/actions", moderatorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void moderateComment_nonModerator_returns403() {
        Map<String, Object> body = Map.of("action", "APPROVE", "reason", "ok");
        ResponseEntity<Map> resp = post("/api/v1/moderation/comments/1/actions", noRoleToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/moderation/sensitive-words ──

    @Test
    @Order(4)
    void createSensitiveWord_returns201() {
        Map<String, Object> body = Map.of("word", unique("badword"), "active", true);
        ResponseEntity<Map> resp = post("/api/v1/moderation/sensitive-words", moderatorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        sensitiveWordId = ((Number) resp.getBody().get("id")).longValue();
    }

    @Test
    void createSensitiveWord_missingWord_returns400() {
        ResponseEntity<Map> resp = post("/api/v1/moderation/sensitive-words", moderatorToken, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSensitiveWord_nonModerator_returns403() {
        ResponseEntity<Map> resp = post("/api/v1/moderation/sensitive-words", noRoleToken,
                Map.of("word", "test"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PUT /api/v1/moderation/sensitive-words/{id} ──

    @Test
    @Order(5)
    void updateSensitiveWord_returns200() {
        assertThat(sensitiveWordId).isNotNull();
        Map<String, Object> body = Map.of("word", unique("updated"), "active", false);
        ResponseEntity<Map> resp = put("/api/v1/moderation/sensitive-words/" + sensitiveWordId,
                moderatorToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── DELETE /api/v1/moderation/sensitive-words/{id} ──

    @Test
    @Order(6)
    void deleteSensitiveWord_returns204() {
        assertThat(sensitiveWordId).isNotNull();
        ResponseEntity<Void> resp = delete("/api/v1/moderation/sensitive-words/" + sensitiveWordId, moderatorToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteSensitiveWord_nonModerator_returns403() {
        ResponseEntity<Void> resp = delete("/api/v1/moderation/sensitive-words/1", noRoleToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/moderation/sensitive-words ──

    @Test
    void listSensitiveWords_asModerator_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/moderation/sensitive-words",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(moderatorToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listSensitiveWords_nonModerator_returns403() {
        ResponseEntity<Map> resp = getMap("/api/v1/moderation/sensitive-words", noRoleToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
