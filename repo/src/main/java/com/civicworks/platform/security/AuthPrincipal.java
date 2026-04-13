package com.civicworks.platform.security;

import java.util.Set;

public record AuthPrincipal(Long userId, String username, Set<Role> roles) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... checkRoles) {
        for (Role r : checkRoles) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
