package com.devknow.auth;

/** Principal autenticado disponible con @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, String fullName, Role role) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}
