package com.devknow;

import static org.junit.jupiter.api.Assertions.*;

import com.devknow.auth.JwtService;
import com.devknow.auth.Role;
import com.devknow.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-secret-with-at-least-32-characters-long";

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setEmail("maria@devknow.com");
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    void issuesAndParsesToken() {
        JwtService jwt = new JwtService(SECRET, Duration.ofHours(1));
        Claims c = jwt.parse(jwt.issue(user()).token());
        assertEquals("maria@devknow.com", c.getSubject());
        assertEquals("ADMIN", c.get("role"));
        assertNotNull(c.getId());
    }

    @Test
    void rejectsTokenSignedWithAnotherKeyAndExpiredTokens() {
        String token = new JwtService(SECRET, Duration.ofHours(1)).issue(user()).token();
        JwtService other = new JwtService("another-secret-with-at-least-32-characters!!", Duration.ofHours(1));
        assertThrows(JwtException.class, () -> other.parse(token));

        JwtService shortLived = new JwtService(SECRET, Duration.ofSeconds(-5));
        String expired = shortLived.issue(user()).token();
        assertThrows(JwtException.class, () -> shortLived.parse(expired));
    }

    @Test
    void rejectsWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corto", Duration.ofHours(1)));
    }
}
