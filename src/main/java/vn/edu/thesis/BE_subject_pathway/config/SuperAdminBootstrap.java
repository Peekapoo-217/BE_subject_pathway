package vn.edu.thesis.BE_subject_pathway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class SuperAdminBootstrap implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public SuperAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${app.bootstrap.super-admin-username:}") String username,
            @Value("${app.bootstrap.super-admin-password:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM public.app_users WHERE role='SUPER_ADMIN'",
                Integer.class);
        if (count != null && count == 0 && !username.isBlank() && !password.isBlank()) {
            jdbc.update(
                    "INSERT INTO public.app_users(username, password_hash, role) VALUES (?, ?, 'SUPER_ADMIN') ON CONFLICT(username) DO NOTHING",
                    username.trim(), encoder.encode(password));
        }
    }
}
