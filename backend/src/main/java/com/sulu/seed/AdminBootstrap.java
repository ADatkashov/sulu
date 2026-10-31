package com.sulu.seed;

import com.sulu.common.SuluProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first platform admin from SULU_ADMIN_EMAIL / SULU_ADMIN_PASSWORD (only if no admin with that email exists). */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcClient db; private final SuluProperties props; private final PasswordEncoder encoder;
    public AdminBootstrap(JdbcClient db, SuluProperties props, PasswordEncoder encoder) { this.db = db; this.props = props; this.encoder = encoder; }

    @Override public void run(ApplicationArguments args) {
        String email = props.admin().email(), pw = props.admin().password();
        if (email == null || email.isBlank() || pw == null || pw.isBlank()) return;
        int n = db.sql("INSERT INTO users(email, password_hash, full_name, role, email_verified) VALUES (:e,:p,'Platform Admin','PLATFORM_ADMIN',TRUE) ON CONFLICT DO NOTHING")
                .param("e", email.trim()).param("p", encoder.encode(pw)).update();
        if (n > 0) log.info("Created platform admin {}", email);
    }
}
