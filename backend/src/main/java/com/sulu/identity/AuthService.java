package com.sulu.identity;

import com.sulu.common.ApiException;
import com.sulu.common.SuluProperties;
import com.sulu.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BgI8uZTi1Vbq1q8sJ9m1mJ4G5y6e";

    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SuluProperties props;
    private final Clock clock;
    private final Environment env;

    public AuthService(JdbcClient db, PasswordEncoder encoder, JwtService jwt, SuluProperties props, Clock clock, Environment env) {
        this.db = db; this.encoder = encoder; this.jwt = jwt; this.props = props; this.clock = clock; this.env = env;
    }

    public record AuthResponse(String accessToken, String refreshToken, int expiresIn, UserDto user) {}

    public static void checkPassword(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 72 || pw.chars().noneMatch(Character::isLetter) || pw.chars().noneMatch(Character::isDigit))
            throw ApiException.bad("WEAK_PASSWORD", "Password must be 8-72 characters and contain a letter and a digit");
    }

    @Transactional
    public AuthResponse register(String email, String password, String fullName, String phone, String role, String language, String city) {
        checkPassword(password);
        if (!"CUSTOMER".equals(role) && !"BUSINESS_OWNER".equals(role))
            throw ApiException.bad("ROLE_NOT_ALLOWED", "Only CUSTOMER or BUSINESS_OWNER can self-register");
        long id;
        try {
            id = db.sql("""
                INSERT INTO users(email, password_hash, full_name, phone, role, language, city)
                VALUES (:e,:p,:n,:ph,:r,:l,:c) RETURNING id""")
                .param("e", email.trim()).param("p", encoder.encode(password)).param("n", fullName.trim()).param("ph", blank(phone))
                .param("r", role).param("l", language == null ? "ru" : language).param("c", blank(city))
                .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "Email is already registered");
        }
        return issue(id);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        Optional<Map<String, Object>> row = db.sql("SELECT id, password_hash, status FROM users WHERE lower(email)=lower(:e)")
                .param("e", email.trim()).query().listOfRows().stream().findFirst();
        // constant-ish time: always run a hash comparison
        String hash = row.map(r -> (String) r.get("password_hash")).orElse(DUMMY_HASH);
        boolean ok = encoder.matches(password, hash) && row.isPresent() && "ACTIVE".equals(row.get().get("status"));
        if (!ok) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password");
        return issue(((Number) row.get().get("id")).longValue());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        String h = JwtService.hash(refreshToken);
        var row = db.sql("SELECT id, user_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash=:h").param("h", h)
                .query().listOfRows().stream().findFirst().orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Invalid refresh token"));
        if (row.get("revoked_at") != null || ((java.sql.Timestamp) row.get("expires_at")).toInstant().isBefore(clock.instant()))
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Invalid refresh token");
        db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE id=:i").param("i", row.get("id")).update(); // rotation
        long userId = ((Number) row.get("user_id")).longValue();
        String status = db.sql("SELECT status FROM users WHERE id=:i").param("i", userId).query(String.class).single();
        if (!"ACTIVE".equals(status)) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Invalid refresh token");
        return issue(userId);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE token_hash=:h AND revoked_at IS NULL").param("h", JwtService.hash(refreshToken)).update();
    }

    @Transactional
    public void requestPasswordReset(String email) {
        var id = db.sql("SELECT id FROM users WHERE lower(email)=lower(:e) AND status='ACTIVE'").param("e", email.trim()).query(Long.class).optional();
        if (id.isEmpty()) return; // never reveal whether an email exists
        String token = jwt.newOpaqueToken();
        db.sql("INSERT INTO password_reset_tokens(user_id, token_hash, expires_at) VALUES (:u,:h,:x)")
          .param("u", id.get()).param("h", JwtService.hash(token)).param("x", java.sql.Timestamp.from(clock.instant().plus(1, ChronoUnit.HOURS))).update();
        // No email provider is configured: the token can only be surfaced in local development.
        if (env.acceptsProfiles(Profiles.of("dev"))) log.warn("DEV ONLY password reset token for {}: {}", email, token);
        else log.info("Password reset requested; no email provider configured, nothing was sent.");
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        checkPassword(newPassword);
        var row = db.sql("SELECT id, user_id, expires_at, used_at FROM password_reset_tokens WHERE token_hash=:h").param("h", JwtService.hash(token))
                .query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.bad("BAD_RESET_TOKEN", "Invalid or expired token"));
        if (row.get("used_at") != null || ((java.sql.Timestamp) row.get("expires_at")).toInstant().isBefore(clock.instant()))
            throw ApiException.bad("BAD_RESET_TOKEN", "Invalid or expired token");
        long userId = ((Number) row.get("user_id")).longValue();
        db.sql("UPDATE password_reset_tokens SET used_at=now() WHERE id=:i").param("i", row.get("id")).update();
        db.sql("UPDATE users SET password_hash=:p, updated_at=now() WHERE id=:u").param("p", encoder.encode(newPassword)).param("u", userId).update();
        db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=:u AND revoked_at IS NULL").param("u", userId).update();
    }

    /** A business invites a professional by email; the invitee sets a password and gets a PROFESSIONAL account linked to the profile. */
    @Transactional
    public AuthResponse acceptInvite(String token, String password, String fullName) {
        checkPassword(password);
        var inv = db.sql("SELECT id, professional_id, email, expires_at, accepted_at FROM professional_invites WHERE token_hash=:h").param("h", JwtService.hash(token))
                .query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.bad("BAD_INVITE", "Invalid or expired invitation"));
        if (inv.get("accepted_at") != null || ((java.sql.Timestamp) inv.get("expires_at")).toInstant().isBefore(clock.instant()))
            throw ApiException.bad("BAD_INVITE", "Invalid or expired invitation");
        long id;
        try {
            id = db.sql("INSERT INTO users(email, password_hash, full_name, role) VALUES (:e,:p,:n,'PROFESSIONAL') RETURNING id")
                    .param("e", inv.get("email")).param("p", encoder.encode(password)).param("n", fullName.trim()).query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "Email is already registered");
        }
        db.sql("UPDATE professionals SET user_id=:u WHERE id=:p AND user_id IS NULL").param("u", id).param("p", inv.get("professional_id")).update();
        db.sql("UPDATE professional_invites SET accepted_at=now() WHERE id=:i").param("i", inv.get("id")).update();
        return issue(id);
    }

    public AuthResponse issue(long userId) {
        String refresh = jwt.newOpaqueToken();
        db.sql("INSERT INTO refresh_tokens(user_id, token_hash, expires_at) VALUES (:u,:h,:x)")
          .param("u", userId).param("h", JwtService.hash(refresh))
          .param("x", java.sql.Timestamp.from(clock.instant().plus(props.jwt().refreshTtlDays(), ChronoUnit.DAYS))).update();
        return new AuthResponse(jwt.accessToken(userId), refresh, jwt.accessTtlSeconds(), UserRepository.get(db, userId));
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
