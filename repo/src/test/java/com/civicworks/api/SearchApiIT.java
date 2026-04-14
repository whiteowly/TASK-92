package com.civicworks.api;

import com.civicworks.platform.security.Role;
import com.civicworks.platform.security.UserEntity;
import com.civicworks.searchanalytics.domain.SearchDocument;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchApiIT extends BaseApiIT {

    private String userToken;
    private UserEntity searchUser;

    @BeforeAll
    void setup() {
        searchUser = createUser(unique("search_user"), "pass123", Role.CONTENT_EDITOR);
        userToken = login(searchUser.getUsername(), "pass123");

        // Seed search documents for general search tests
        createSearchDocument("Water Bill Policy", "POLICY", 1001L);
        createSearchDocument("Waste Collection Schedule", "NEWS", 1002L);
        createSearchDocument("Park Events Calendar", "EVENT", 1003L);

        // Seed 12 documents with "Municipal" in title for typeahead limit/ranking tests
        for (int i = 1; i <= 12; i++) {
            createSearchDocument("Municipal Service Report " + i, "NEWS", 2000L + i);
        }
    }

    // ── GET /api/v1/public/search ──

    @Test
    void publicSearch_noAuth_returns200() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/search?q=water");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void publicSearch_withFilters() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/search?q=&recordType=POLICY&page=0&size=5");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicSearch_emptyQuery_returns200() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/public/search?page=0&size=10");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/public/search/typeahead ──

    @Test
    void publicTypeahead_withShortQuery_returnsEmpty200() {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/public/search/typeahead?q=w", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("[]");
    }

    @Test
    void publicTypeahead_withLongerQuery_returns200AndSuggestions() {
        ResponseEntity<String> resp = rest.getForEntity(
                "/api/v1/public/search/typeahead?q=Water+Bill", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEqualTo("[]");
        assertThat(resp.getBody()).contains("Water Bill Policy");
    }

    @Test
    void publicTypeahead_returnsAtMost10() {
        // 12 "Municipal Service Report" docs seeded; typeahead LIMIT 10 should cap results
        ResponseEntity<List> resp = rest.getForEntity(
                "/api/v1/public/search/typeahead?q=Municipal+Service", List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().size()).isLessThanOrEqualTo(10);
    }

    @Test
    void publicTypeahead_resultsAreRankedDeterministically() {
        ResponseEntity<List> resp1 = rest.getForEntity(
                "/api/v1/public/search/typeahead?q=Municipal+Service", List.class);
        ResponseEntity<List> resp2 = rest.getForEntity(
                "/api/v1/public/search/typeahead?q=Municipal+Service", List.class);

        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getBody()).isEqualTo(resp2.getBody());
    }

    // ── GET /api/v1/public/search/recommendations ──

    @Test
    void publicRecommendations_returns200() {
        ResponseEntity<Object[]> resp = rest.getForEntity("/api/v1/public/search/recommendations", Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/search (authenticated) ──

    @Test
    @Order(1)
    void authSearch_returns200() {
        ResponseEntity<Map> resp = getMap("/api/v1/search?q=water", userToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
    }

    @Test
    void authSearch_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/search?q=test");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authSearch_withAllFilters() {
        ResponseEntity<Map> resp = getMap(
                "/api/v1/search?q=park&recordType=EVENT&category=test&sort=title&page=0&size=5",
                userToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/search/typeahead (authenticated) ──

    @Test
    void authTypeahead_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/search/typeahead?q=was",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(userToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void authTypeahead_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/search/typeahead?q=test");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /api/v1/search/recommendations (authenticated) ──

    @Test
    void authRecommendations_returns200() {
        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/search/recommendations",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(userToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/v1/search/history ──

    @Test
    @Order(2)
    void searchHistory_returns200() {
        // Perform a search first to generate history
        getMap("/api/v1/search?q=historytest", userToken);

        ResponseEntity<Object[]> resp = rest.exchange("/api/v1/search/history",
                HttpMethod.GET, new HttpEntity<>(bearerHeaders(userToken)), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchHistory_noAuth_returns401() {
        ResponseEntity<Map> resp = getNoAuth("/api/v1/search/history");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── DELETE /api/v1/search/history ──

    @Test
    @Order(3)
    void deleteSearchHistory_returns204() {
        ResponseEntity<Void> resp = delete("/api/v1/search/history", userToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteSearchHistory_noAuth_returns401() {
        ResponseEntity<Map> resp = exchangeNoAuth("/api/v1/search/history", HttpMethod.DELETE, null, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
