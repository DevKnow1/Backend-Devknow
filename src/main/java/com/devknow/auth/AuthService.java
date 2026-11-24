package com.devknow.auth;

import com.devknow.auth.AuthDtos.*;
import com.devknow.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RevokedTokenRepository revoked;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, RevokedTokenRepository revoked, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.revoked = revoked;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** RF-01: el registro público siempre crea usuarios con rol USER. */
    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo electrónico.");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.USER);
        return UserResponse.from(users.save(u));
    }

    /** RF-02: mensaje genérico, sin revelar si el correo existe. */
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().trim().toLowerCase())
            .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        JwtService.IssuedToken t = jwt.issue(u);
        return new AuthResponse(t.token(), "Bearer", jwt.expiresInSeconds(), UserResponse.from(u));
    }

    /** RF-03: invalida el token (lista de revocación por jti). */
    @Transactional
    public void logout(String token) {
        try {
            Claims c = jwt.parse(token);
            if (!revoked.existsById(c.getId())) {
                revoked.save(new RevokedToken(c.getId(), c.getExpiration().toInstant()));
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // token ya inválido: nada que revocar
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpiredRevocations() {
        revoked.deleteByExpiresAtBefore(Instant.now());
    }
}
