package com.devknow.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Valida el JWT, comprueba que no esté revocado (logout) y carga el rol vigente desde la BD. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final RevokedTokenRepository revoked;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, RevokedTokenRepository revoked, UserRepository users) {
        this.jwt = jwt;
        this.revoked = revoked;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(header.substring(7).trim());
                if (!revoked.existsById(c.getId())) {
                    Long uid = ((Number) c.get("uid")).longValue();
                    users.findById(uid).ifPresent(u -> {
                        AuthUser principal = new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
                }
            } catch (JwtException | IllegalArgumentException | ClassCastException | NullPointerException ignored) {
                // token inválido o expirado: la solicitud sigue sin autenticación y será rechazada con 401
            }
        }
        chain.doFilter(req, res);
    }
}
