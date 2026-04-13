package com.civicworks.dispatch;

import com.civicworks.dispatch.api.DispatchController;
import com.civicworks.dispatch.application.DispatchService;
import com.civicworks.dispatch.domain.DispatchOrder;
import com.civicworks.platform.security.AuthPrincipal;
import com.civicworks.platform.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A) Object-level authorization for /dispatch/orders.
 *  - DRIVER callers must only see their own orders.
 *  - DISPATCHER callers must see the global queue.
 */
class DispatchListAuthzTest {

    private DispatchService svc;
    private DispatchController controller;

    @BeforeEach
    void setUp() {
        svc = mock(DispatchService.class);
        controller = new DispatchController(svc);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId, Role... roles) {
        AuthPrincipal p = new AuthPrincipal(userId, "u" + userId, Set.of(roles));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, List.of()));
    }

    @Test
    void driver_seesOnlyOwnOrders_neverGlobal() {
        authenticateAs(42L, Role.DRIVER);
        DispatchOrder mine = new DispatchOrder();
        mine.setId(1L); mine.setAssignedDriverId(7L);
        Page<DispatchOrder> driverPage = new PageImpl<>(List.of(mine));
        when(svc.listDriverOrdersForUser(eq(42L), eq(0), eq(20))).thenReturn(driverPage);

        var resp = controller.listOrders(0, 20);
        assertEquals(1, resp.getBody().getNumberOfElements());
        verify(svc, times(1)).listDriverOrdersForUser(42L, 0, 20);
        verify(svc, never()).listOrders(anyInt(), anyInt());
    }

    @Test
    void driver_withNoDriverProfile_getsEmptyPage_noGlobalLeak() {
        authenticateAs(42L, Role.DRIVER);
        when(svc.listDriverOrdersForUser(eq(42L), eq(0), eq(20))).thenReturn(Page.empty());

        var resp = controller.listOrders(0, 20);
        assertEquals(0, resp.getBody().getNumberOfElements());
        verify(svc, never()).listOrders(anyInt(), anyInt());
    }

    @Test
    void dispatcher_seesGlobalListing() {
        authenticateAs(7L, Role.DISPATCHER);
        DispatchOrder a = new DispatchOrder(); a.setId(1L);
        DispatchOrder b = new DispatchOrder(); b.setId(2L);
        when(svc.listOrders(0, 20)).thenReturn(new PageImpl<>(List.of(a, b)));

        var resp = controller.listOrders(0, 20);
        assertEquals(2, resp.getBody().getNumberOfElements());
        verify(svc, times(1)).listOrders(0, 20);
        verify(svc, never()).listDriverOrdersForUser(anyLong(), anyInt(), anyInt());
    }

    @Test
    void dispatcherAndDriver_dispatcherWins() {
        authenticateAs(9L, Role.DISPATCHER, Role.DRIVER);
        when(svc.listOrders(0, 20)).thenReturn(Page.empty());

        controller.listOrders(0, 20);
        verify(svc, times(1)).listOrders(0, 20);
        verify(svc, never()).listDriverOrdersForUser(anyLong(), anyInt(), anyInt());
    }
}
