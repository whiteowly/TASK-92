package com.civicworks.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every demo credential listed in README.md "Seeded Credentials"
 * actually resolves to a working login against the live Auth endpoint, and
 * that the bearer token issued by login bears the documented role.
 *
 * <p>This is a hard contract: the README promises one user per role with a
 * shared dev-only password. If a demo username is renamed or the password
 * hash drifts in V1/V6, this test fails before the README does.
 */
class DemoCredentialsLoginIT extends BaseApiIT {

    @ParameterizedTest(name = "demo login: {0} ({1})")
    @CsvSource({
            "admin,      SYSTEM_ADMIN",
            "editor,     CONTENT_EDITOR",
            "moderator,  MODERATOR",
            "clerk,      BILLING_CLERK",
            "dispatcher, DISPATCHER",
            "driver,     DRIVER",
            "auditor,    AUDITOR"
    })
    void demoUser_canLogIn_andHasDocumentedRole(String username, String expectedRole) {
        Map<String, Object> body = Map.of("username", username, "password", "admin123");
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);

        assertThat(resp.getStatusCode())
                .as("README documents %s/admin123 as a working demo login", username)
                .isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = resp.getBody();
        assertThat(respBody).isNotNull();
        assertThat(respBody.get("accessToken"))
                .as("login response must carry an opaque bearer token")
                .isInstanceOf(String.class);
        assertThat((String) respBody.get("accessToken")).startsWith("cwk_sess_");
        // The login response includes the user's roles so the client can pick
        // a default landing surface; we use it here to verify the seed wired
        // each demo username to the role the README promises.
        Object roles = respBody.get("roles");
        assertThat(roles)
                .as("login response must include the user's roles")
                .isInstanceOf(java.util.Collection.class);
        @SuppressWarnings("unchecked")
        java.util.Collection<Object> roleCollection = (java.util.Collection<Object>) roles;
        assertThat(roleCollection).contains(expectedRole);
    }

    @Test
    void demoUser_wrongPassword_returns401() {
        // postNoAuth handles Java HttpURLConnection's HttpRetryException on 401
        // — see BaseApiIT.postNoAuth for the rationale.
        Map<String, Object> body = Map.of("username", "editor", "password", "not-the-password");
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = postNoAuth("/api/v1/auth/login", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
