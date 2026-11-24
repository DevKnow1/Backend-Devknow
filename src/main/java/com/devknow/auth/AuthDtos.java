package com.devknow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres") String fullName,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo electrónico inválido") @Size(max = 255) String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "La contraseña debe tener entre 8 y 72 caracteres, con letras y números") String password) {}

    public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {}

    public record UserResponse(Long id, String fullName, String email, Role role) {
        public static UserResponse from(User u) { return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole()); }
        public static UserResponse from(AuthUser u) { return new UserResponse(u.id(), u.fullName(), u.email(), u.role()); }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {}
}
