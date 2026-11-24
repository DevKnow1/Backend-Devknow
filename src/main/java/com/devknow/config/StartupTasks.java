package com.devknow.config;

import com.devknow.auth.Role;
import com.devknow.auth.User;
import com.devknow.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el administrador inicial (el registro público solo crea usuarios con rol USER). */
@Component
public class StartupTasks implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupTasks.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public StartupTasks(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin a = props.admin();
        if (a.email() == null || a.email().isBlank() || a.password() == null || a.password().isBlank()) return;
        String email = a.email().trim().toLowerCase();
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setEmail(email);
        u.setFullName(a.fullName());
        u.setPasswordHash(encoder.encode(a.password()));
        u.setRole(Role.ADMIN);
        users.save(u);
        log.info("Administrador inicial creado: {}", email);
    }
}
