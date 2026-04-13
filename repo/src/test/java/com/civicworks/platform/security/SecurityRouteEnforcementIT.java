package com.civicworks.platform.security;

import com.civicworks.billing.application.AccountService;
import com.civicworks.billing.domain.Account;
import com.civicworks.notifications.application.NotificationService;
import com.civicworks.notifications.domain.InAppMessage;
import com.civicworks.platform.audit.AuditLogRepository;
import com.civicworks.platform.audit.AuditService;
import com.civicworks.platform.config.SystemConfigRepository;
import com.civicworks.platform.idempotency.IdempotencyService;
import com.civicworks.platform.error.BusinessException;
import com.civicworks.settlement.application.SettlementService;
import com.civicworks.settlement.domain.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real Spring Security filter chain integration. Unlike the earlier
 * annotation-only contract test, this one boots {@link SecurityConfig},
 * {@link TokenAuthenticationFilter} and {@link AuthEntryPoint} against a
 * representative set of controllers (admin, settlement, notifications,
 * billing/account) and drives HTTP requests through MockMvc. Each test
 * pins one of:
 * <ul>
 *   <li>unauthenticated → 401 with project envelope</li>
 *   <li>authenticated with wrong role → 403</li>
 *   <li>authenticated with correct role → 2xx</li>
 *   <li>object-level isolation (ack someone else's message) → 403 envelope</li>
 * </ul>
 */
@WebMvcTest(controllers = {
        com.civicworks.platform.config.AdminController.class,
        com.civicworks.settlement.api.SettlementController.class,
        com.civicworks.notifications.api.NotificationController.class,
        com.civicworks.billing.api.AccountController.class
})
@Import({
        SecurityConfig.class,
        TokenAuthenticationFilter.class,
        PasswordEncoderConfig.class,
        com.civicworks.platform.error.GlobalExceptionHandler.class,
        com.civicworks.platform.config.RequestIdFilter.class
})
class SecurityRouteEnforcementIT {

    @Autowired private MockMvc mockMvc;

    // All downstream dependencies of the controllers and the security filter
    // chain are mocked — this keeps the slice web-only (no DB, no JPA).
    @MockBean private AuthService authService;
    @MockBean private AuditService auditService;
    @MockBean private AuditLogRepository auditLogRepository;
    @MockBean private SystemConfigRepository systemConfigRepository;
    @MockBean private SettlementService settlementService;
    @MockBean private NotificationService notificationService;
    @MockBean private AccountService accountService;
    @MockBean private IdempotencyService idempotencyService;

    /** Returns a request builder with a valid Bearer token that validates to the given role set. */
    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b, long userId, Role... roles) {
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
        reset(authService, settlementService, notificationService, accountService,
                idempotencyService, auditService, systemConfigRepository, auditLogRepository);
    }

    // ------------ unauthenticated → 401 envelope ------------

    @Test
    void unauthenticated_adminConfig_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    void unauthenticated_postPayment_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/payments")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":1,\"amount\":\"1.00\","
                                + "\"method\":\"CASH\",\"settlementType\":\"FULL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_residentIdLookup_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/billing/accounts/1/resident-id"))
                .andExpect(status().isUnauthorized());
    }

    // ------------ authenticated-but-wrong-role → 403 ------------

    @Test
    void wrongRole_adminConfig_returns403() throws Exception {
        mockMvc.perform(asUser(get("/api/v1/admin/system-config"), 7L, Role.BILLING_CLERK))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_postPayment_returns403() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/settlements/payments"), 7L, Role.DRIVER)
                        .header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":1,\"amount\":\"1.00\","
                                + "\"method\":\"CASH\",\"settlementType\":\"FULL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_discrepancyResolve_returns403() throws Exception {
        // discrepancy resolve is SYSTEM_ADMIN only.
        mockMvc.perform(asUser(post("/api/v1/settlements/discrepancies/1/resolve"), 7L, Role.AUDITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongRole_residentIdSet_returns403() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/billing/accounts/1/resident-id"), 7L, Role.DRIVER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residentId\":\"RES-1\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------ correct role → 2xx ------------

    @Test
    void correctRole_adminConfigRead_succeeds() throws Exception {
        when(systemConfigRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(asUser(get("/api/v1/admin/system-config"), 1L, Role.SYSTEM_ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void correctRole_postPayment_succeeds() throws Exception {
        Payment p = new Payment();
        when(idempotencyService.executeIdempotent(any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok(p));
        mockMvc.perform(asUser(post("/api/v1/settlements/payments"), 1L, Role.BILLING_CLERK)
                        .header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":1,\"amount\":\"1.00\","
                                + "\"method\":\"CASH\",\"settlementType\":\"FULL\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void correctRole_residentIdRead_succeeds() throws Exception {
        Account a = new Account();
        a.setId(1L); a.setUserId(9L); a.setName("Ada"); a.setStatus("ACTIVE");
        a.setEncryptedResidentId("ciphertext");
        when(accountService.findById(1L)).thenReturn(Optional.of(a));
        when(accountService.viewResidentId(eq(a), eq(true))).thenReturn("RES-12345");

        mockMvc.perform(asUser(get("/api/v1/billing/accounts/1/resident-id"), 1L, Role.SYSTEM_ADMIN))
                .andExpect(status().isOk());
    }

    // ------------ object-level authorization ------------

    @Test
    void objectLevel_cannotAckSomeoneElsesMessage() throws Exception {
        // The service enforces object-level isolation: user 7 cannot ack a
        // message whose recipient is user 8. The service throws a
        // BusinessException.forbidden which the global handler surfaces as
        // 403 with the project envelope.
        when(notificationService.acknowledgeMessage(42L, 7L))
                .thenThrow(BusinessException.forbidden("Not the recipient"));

        mockMvc.perform(asUser(post("/api/v1/notifications/messages/42/ack"), 7L, Role.BILLING_CLERK))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.message").value("Not the recipient"));
    }

    @Test
    void objectLevel_ackOwnMessage_succeeds() throws Exception {
        InAppMessage m = new InAppMessage();
        m.setId(42L); m.setRecipientId(7L);
        when(notificationService.acknowledgeMessage(42L, 7L)).thenReturn(m);

        mockMvc.perform(asUser(post("/api/v1/notifications/messages/42/ack"), 7L, Role.BILLING_CLERK))
                .andExpect(status().isOk());
    }

    @Test
    void bearerTokenInvalid_returns401() throws Exception {
        when(authService.validateToken("cwk_sess_bad"))
                .thenReturn(AuthService.SessionValidation.invalid(
                        com.civicworks.platform.error.ErrorCode.SESSION_INVALID));

        mockMvc.perform(get("/api/v1/admin/system-config")
                        .header("Authorization", "Bearer cwk_sess_bad"))
                .andExpect(status().isUnauthorized());
    }
}
