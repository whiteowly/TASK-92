package com.civicworks.platform.security;

import com.civicworks.billing.api.AccountController;
import com.civicworks.billing.api.BillingController;
import com.civicworks.notifications.api.NotificationController;
import com.civicworks.platform.config.AdminController;
import com.civicworks.settlement.api.SettlementController;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for route-level authorization.
 *
 * <p>SecurityConfig permits unauthenticated access to a narrow allow-list
 * (login, /api/v1/public/**, actuator health/info). Every other controller
 * method exposed at /api/v1/** must carry a {@link PreAuthorize}
 * annotation so {@code anyRequest().authenticated()} combined with method
 * security actually produces 401/403 on unauthenticated/unauthorized
 * callers.
 *
 * <p>These tests run without a Spring context: they read the source of
 * truth (the annotations) directly so a missing annotation fails loudly
 * instead of silently opening an endpoint.
 */
class EndpointAuthorizationContractTest {

    /** Controllers with at least one admin/internal endpoint that must be SYSTEM_ADMIN only. */
    @TestFactory
    Stream<DynamicTest> adminEndpoints_requireSystemAdmin() {
        return Stream.of(
                method(AdminController.class, "updateConfig"),
                method(AdminController.class, "getConfig"),
                method(NotificationController.class, "createTemplate"),
                method(NotificationController.class, "updateTemplate"),
                method(NotificationController.class, "listOutbox"),
                method(NotificationController.class, "markExported"),
                method(NotificationController.class, "createReminder"),
                method(NotificationController.class, "listReminders")
        ).map(m -> DynamicTest.dynamicTest(
                m.getDeclaringClass().getSimpleName() + "#" + m.getName(),
                () -> {
                    PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                    assertNotNull(pa, m + " must declare @PreAuthorize");
                    assertTrue(pa.value().contains("SYSTEM_ADMIN"),
                            m + " must restrict to SYSTEM_ADMIN; was: " + pa.value());
                }));
    }

    @TestFactory
    Stream<DynamicTest> settlementEndpoints_requireBillingClerk() {
        return Stream.of(
                method(SettlementController.class, "postPayment"),
                method(SettlementController.class, "reversePayment")
        ).map(m -> DynamicTest.dynamicTest(
                m.getDeclaringClass().getSimpleName() + "#" + m.getName(),
                () -> {
                    PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                    assertNotNull(pa, m + " must declare @PreAuthorize");
                    assertTrue(pa.value().contains("BILLING_CLERK"),
                            m + " must restrict to BILLING_CLERK; was: " + pa.value());
                }));
    }

    @Test
    void residentIdSet_limitedToAdminOrBillingClerk() throws Exception {
        Method m = AccountController.class.getMethod(
                "setResidentId", Long.class, AccountController.ResidentIdRequest.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa);
        String v = pa.value();
        // Must admit SYSTEM_ADMIN and BILLING_CLERK, and no public/unauthenticated access.
        assertTrue(v.contains("SYSTEM_ADMIN") && v.contains("BILLING_CLERK"),
                "residentId set must be SYSTEM_ADMIN/BILLING_CLERK only: " + v);
    }

    @Test
    void discrepancyResolve_requiresSystemAdmin() throws Exception {
        Method m = method(SettlementController.class, "resolveDiscrepancy");
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa);
        assertTrue(pa.value().contains("SYSTEM_ADMIN"),
                "discrepancy resolve must be SYSTEM_ADMIN only: " + pa.value());
    }

    @Test
    void everyBillingAndSettlementAndAdminControllerMethod_hasPreAuthorize() {
        // Any HTTP-mapped method on these controllers MUST declare @PreAuthorize,
        // preventing a future contributor from silently exposing a protected
        // route by omitting the annotation. NotificationController is
        // intentionally excluded because some endpoints (list my messages,
        // ack my message) are open to any authenticated user and their
        // object-level scoping is enforced in NotificationService.
        for (Class<?> c : List.of(BillingController.class, SettlementController.class,
                AdminController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                if (isHttpMapped(m)) {
                    assertNotNull(m.getAnnotation(PreAuthorize.class),
                            "Missing @PreAuthorize on " + c.getSimpleName() + "#" + m.getName());
                }
            }
        }
    }

    private static boolean isHttpMapped(Method m) {
        return m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class);
    }

    private static Method method(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new AssertionError("no method " + name + " on " + c);
    }
}
