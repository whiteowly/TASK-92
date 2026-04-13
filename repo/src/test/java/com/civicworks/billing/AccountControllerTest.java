package com.civicworks.billing;

import com.civicworks.billing.api.AccountController;
import com.civicworks.billing.application.AccountService;
import com.civicworks.billing.domain.Account;
import com.civicworks.platform.error.GlobalExceptionHandler;
import com.civicworks.platform.security.AuthPrincipal;
import com.civicworks.platform.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Wire/contract tests for {@link AccountController}.
 *
 * <p>Standalone MockMvc is used to match the project's existing test style
 * (see {@code BillingRunIdempotencyIT}). Standalone setup does NOT enforce
 * {@link PreAuthorize}, so authorization is asserted separately by inspecting
 * the annotation metadata on each handler method.
 */
class AccountControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        AccountController controller = new AccountController(accountService);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId, Role... roles) {
        Set<Role> roleSet = Set.of(roles);
        AuthPrincipal principal = new AuthPrincipal(userId, "u" + userId, roleSet);
        var authorities = roleSet.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", authorities));
    }

    private static Account account(Long id, String encrypted) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(99L);
        a.setName("Acme");
        a.setStatus("ACTIVE");
        a.setEncryptedResidentId(encrypted);
        a.setResidentIdHash("hash-" + id);
        return a;
    }

    // ---- POST /resident-id ------------------------------------------------

    @Test
    void post_setsResidentId_andReturnsRoleSafeView() throws Exception {
        authenticateAs(1L, Role.BILLING_CLERK);
        Account stored = account(42L, "ciphertext-blob");
        when(accountService.setResidentId(eq(42L), eq("RES-123"), eq(1L)))
                .thenReturn(stored);
        when(accountService.viewResidentId(stored, false)).thenReturn("****-123");

        mockMvc.perform(post("/api/v1/billing/accounts/42/resident-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("residentId", "RES-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(42)))
                .andExpect(jsonPath("$.residentId", is("****-123")))
                .andExpect(jsonPath("$.masked", is(true)))
                // Response shape must NOT leak ciphertext or hash.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ciphertext-blob"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("hash-42"))))
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist());

        verify(accountService).setResidentId(42L, "RES-123", 1L);
        verify(accountService).viewResidentId(stored, false);
    }

    @Test
    void post_blankResidentId_returns400() throws Exception {
        authenticateAs(1L, Role.BILLING_CLERK);
        mockMvc.perform(post("/api/v1/billing/accounts/42/resident-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("residentId", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(accountService);
    }

    @Test
    void post_missingBody_returns400() throws Exception {
        authenticateAs(1L, Role.BILLING_CLERK);
        mockMvc.perform(post("/api/v1/billing/accounts/42/resident-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---- GET /resident-id -------------------------------------------------

    @Test
    void get_privilegedAdmin_seesPlaintext() throws Exception {
        authenticateAs(2L, Role.SYSTEM_ADMIN);
        Account stored = account(42L, "ciphertext-blob");
        when(accountService.findById(42L)).thenReturn(Optional.of(stored));
        when(accountService.viewResidentId(stored, true)).thenReturn("RES-123");

        mockMvc.perform(get("/api/v1/billing/accounts/42/resident-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residentId", is("RES-123")))
                .andExpect(jsonPath("$.masked", is(false)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ciphertext-blob"))));
    }

    @Test
    void get_nonPrivileged_seesMasked() throws Exception {
        authenticateAs(3L, Role.BILLING_CLERK);
        Account stored = account(42L, "ciphertext-blob");
        when(accountService.findById(42L)).thenReturn(Optional.of(stored));
        when(accountService.viewResidentId(stored, false)).thenReturn("****-123");

        mockMvc.perform(get("/api/v1/billing/accounts/42/resident-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residentId", is("****-123")))
                .andExpect(jsonPath("$.masked", is(true)));
    }

    @Test
    void get_missingAccount_returns404() throws Exception {
        authenticateAs(2L, Role.SYSTEM_ADMIN);
        when(accountService.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/billing/accounts/99/resident-id"))
                .andExpect(status().isNotFound());
    }

    // ---- GET /by-resident-id ---------------------------------------------

    @Test
    void searchByResidentId_returnsMinimalSummary_noPii() throws Exception {
        authenticateAs(1L, Role.BILLING_CLERK);
        Account stored = account(42L, "ciphertext-blob");
        when(accountService.findByResidentId("RES-123")).thenReturn(Optional.of(stored));

        mockMvc.perform(get("/api/v1/billing/accounts/by-resident-id").param("q", "RES-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(42)))
                .andExpect(jsonPath("$.userId", is(99)))
                .andExpect(jsonPath("$.name", is("Acme")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.residentId").doesNotExist())
                .andExpect(jsonPath("$.encryptedResidentId").doesNotExist())
                .andExpect(jsonPath("$.residentIdHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ciphertext-blob"))));
    }

    // ---- @PreAuthorize annotation pinning --------------------------------

    @Test
    void preAuthorizeAnnotations_areConfiguredAsExpected() throws Exception {
        Method post = AccountController.class.getMethod(
                "setResidentId", Long.class, AccountController.ResidentIdRequest.class);
        Method get = AccountController.class.getMethod("getResidentId", Long.class);
        Method search = AccountController.class.getMethod("findByResidentId", String.class);

        assertEquals("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK')",
                post.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK', 'AUDITOR')",
                get.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAnyRole('SYSTEM_ADMIN', 'BILLING_CLERK')",
                search.getAnnotation(PreAuthorize.class).value());
    }

    // ---- Privileged-role logic (tested via service interaction) ----------

    @Test
    void post_privilegedAuditor_isNotAuthorizedForWrite_butReadIsPrivileged() throws Exception {
        // POST PreAuthorize excludes AUDITOR — pinned via annotation test above.
        // Here we cover that AUDITOR on the GET path drives the privileged
        // service call (privileged=true).
        authenticateAs(7L, Role.AUDITOR);
        Account stored = account(42L, "ciphertext-blob");
        when(accountService.findById(42L)).thenReturn(Optional.of(stored));
        when(accountService.viewResidentId(stored, true)).thenReturn("RES-123");

        mockMvc.perform(get("/api/v1/billing/accounts/42/resident-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked", is(false)));
        verify(accountService).viewResidentId(stored, true);
    }

}
