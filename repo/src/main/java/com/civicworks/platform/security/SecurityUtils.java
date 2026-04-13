package com.civicworks.platform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }

    public static Long currentUserId() {
        AuthPrincipal p = currentPrincipal();
        return p != null ? p.userId() : null;
    }
}
