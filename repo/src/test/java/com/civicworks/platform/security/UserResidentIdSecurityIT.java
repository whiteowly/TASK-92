package com.civicworks.platform.security;

import com.civicworks.platform.audit.AuditLogRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.config.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end security + response-shape contract for user resident-id
 * endpoints. Runs the real Spring Security filter chain against the
 * controller via {@link WebMvcTest} — no DB, no JPA.
 *
 * <p>Pinned invariants:
 * <ul>
 *   <li>Unauthenticated callers get {@code 401} with the standard envelope.</li>
 *   <li>Authenticated callers with insufficient role get {@code 403}.</li>
 *   <li>Allowed roles get {@code 2xx} and:
 *     <ul>
 *       <li>SYSTEM_ADMIN / AUDITOR see the full plaintext resident-id.</li>
 *       <li>BILLING_CLERK (non-privileged reader) sees the masked form.</li>
 *     </ul>
 *   </li>
 *   <li>Response JSON NEVER contains {@code encryptedResidentId} or
 *       {@code residentIdHash} fields.</li>
 * </ul>
 */
@WebMvcTest(controllers = UserResidentIdController.class)
@Import({
        SecurityConfig.class,
        TokenAuthenticationFilter.class,
        PasswordEncoderConfig.class,
        com.civicworks.platform.error.GlobalExceptionHandler.class,
        com.civicworks.platform.config.RequestIdFilter.class
})
class UserResidentIdSecurityIT {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private AuditService auditService;
    @MockBean private AuditLogRepository auditLogRepository;
    @MockBean private SystemConfigRepository systemConfigRepository;
    @MockBean private UserResidentIdService userResidentIdService;

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b,
                                                  long userId, Role... roles) {
        String token = "cwk_sess_" + userId + "-" + (roles.length > 0 ? roles[0].name() : "NONE");
        UserEntity u = new UserEntity();
        u.setId(userId); u.setUsername("u" + userId);
        u.setRoles(Set.of(roles));
        u.setStatus(UserEntity.UserStatus.ACTIVE);
        when(authService.validateToken(token))
                .thenReturn(AuthService.SessionValidation.valid(u, new AuthSession()));
        return b.header("Authorization", "Bearer " + token);
    }

    @BeforeEach
    void resetMocks() {
        reset(authService, userResidentIdService, auditService,
                auditLogRepository, systemConfigRepository);
    }

    private UserEntity userWithEncryptedId() {
        UserEntity u = new UserEntity();
        u.setId(77L); u.setUsername("alice");
        u.setStatus(UserEntity.UserStatus.ACTIVE);
        // Opaque ciphertext stand-in — test only cares the service returns
        // the view-string unchanged; real encryption semantics are covered
        // in UserResidentIdServiceTest.
        u.setEncryptedResidentId("ciphertext-not-returned-to-client");
        u.setResidentIdHash("hash-not-returned-to-client");
        return u;
    }

    // --- 401 unauthenticated ---

    @Test
    void unauthenticated_get_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/77/resident-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    void unauthenticated_post_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/77/resident-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residentId\":\"RES\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_lookup_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/by-resident-id?q=RES"))
                .andExpect(status().isUnauthorized());
    }

    // --- 403 wrong role ---

    @Test
    void wrongRole_set_returns403() throws Exception {
        // setResidentId is SYSTEM_ADMIN only — AUDITOR is not sufficient.
        mockMvc.perform(asUser(post("/api/v1/users/77/resident-id"), 9L, Role.AUDITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residentId\":\"RES\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_set_byDriver_returns403() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/users/77/resident-id"), 9L, Role.DRIVER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residentId\":\"RES\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_get_byDriver_returns403() throws Exception {
        mockMvc.perform(asUser(get("/api/v1/users/77/resident-id"), 9L, Role.DRIVER))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_lookup_byBillingClerk_returns403() throws Exception {
        // by-resident-id is SYSTEM_ADMIN/AUDITOR only.
        mockMvc.perform(asUser(get("/api/v1/users/by-resident-id?q=RES"), 9L, Role.BILLING_CLERK))
                .andExpect(status().isForbidden());
    }

    // --- 2xx correct role ---

    @Test
    void systemAdmin_set_succeeds_andReturnsPlaintextView_neverRawProtectionFields() throws Exception {
        UserEntity saved = userWithEncryptedId();
        when(userResidentIdService.setResidentId(eq(77L), eq("RES-USER-999"), any()))
                .thenReturn(saved);
        when(userResidentIdService.viewResidentId(saved, true)).thenReturn("RES-USER-999");

        mockMvc.perform(asUser(post("/api/v1/users/77/resident-id"), 1L, Role.SYSTEM_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residentId\":\"RES-USER-999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(77))
                .andExpect(jsonPath("$.residentId").value("RES-USER-999"))
                .andExpect(jsonPath("$.masked").value(false))
                // The DTO MUST NOT echo ciphertext or hash.
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist());
    }

    @Test
    void auditor_get_receivesPlaintext() throws Exception {
        UserEntity u = userWithEncryptedId();
        when(userResidentIdService.findById(77L)).thenReturn(Optional.of(u));
        when(userResidentIdService.viewResidentId(u, true)).thenReturn("RES-USER-999");

        mockMvc.perform(asUser(get("/api/v1/users/77/resident-id"), 2L, Role.AUDITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residentId").value("RES-USER-999"))
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist());
    }

    @Test
    void billingClerk_get_receivesMaskedOnly() throws Exception {
        UserEntity u = userWithEncryptedId();
        when(userResidentIdService.findById(77L)).thenReturn(Optional.of(u));
        when(userResidentIdService.viewResidentId(u, false)).thenReturn("****-999");

        mockMvc.perform(asUser(get("/api/v1/users/77/resident-id"), 3L, Role.BILLING_CLERK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residentId").value("****-999"))
                .andExpect(jsonPath("$.masked").value(true))
                // Even the masked view MUST NOT echo the raw plaintext.
                .andExpect(jsonPath("$.residentId", is(not("RES-USER-999"))))
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist());
    }

    @Test
    void systemAdmin_lookupByResidentId_returnsSafeSummary() throws Exception {
        UserEntity u = userWithEncryptedId();
        when(userResidentIdService.findByResidentId("RES-USER-999")).thenReturn(Optional.of(u));

        mockMvc.perform(asUser(get("/api/v1/users/by-resident-id?q=RES-USER-999"), 1L, Role.SYSTEM_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(77))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist())
                .andExpect(jsonPath("$.residentId").doesNotExist());
    }

    @Test
    void lookupByResidentId_blankQuery_returns400Envelope() throws Exception {
        mockMvc.perform(asUser(get("/api/v1/users/by-resident-id?q="), 1L, Role.SYSTEM_ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void lookupByResidentId_notFound_returns404Envelope() throws Exception {
        when(userResidentIdService.findByResidentId("RES-MISSING"))
                .thenReturn(Optional.empty());

        mockMvc.perform(asUser(get("/api/v1/users/by-resident-id?q=RES-MISSING"), 1L, Role.SYSTEM_ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").exists());
    }
}
