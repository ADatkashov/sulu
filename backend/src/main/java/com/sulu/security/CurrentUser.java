package com.sulu.security;

import com.sulu.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentUser(long id, String role) {
    public static CurrentUser require() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CurrentUser u) return u;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    /** For public endpoints that personalize when a user is present. */
    public static CurrentUser optional() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof CurrentUser u) ? u : null;
    }
    public boolean isAdmin() { return "PLATFORM_ADMIN".equals(role); }
}
