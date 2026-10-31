package com.sulu.identity;

import com.sulu.common.ApiException;
import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final JdbcClient db;
    private final PasswordEncoder encoder;
    public MeController(JdbcClient db, PasswordEncoder encoder) { this.db = db; this.encoder = encoder; }

    public record UpdateReq(@Size(min = 1, max = 120) String fullName, @Size(max = 32) String phone,
                            @Pattern(regexp = "kk|ru|en|tr") String language, @Size(max = 80) String city,
                            Boolean notifyEmail, Boolean notifyPush) {}
    public record PasswordReq(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @GetMapping public UserDto me() { return UserRepository.get(db, CurrentUser.require().id()); }

    @PatchMapping
    public UserDto update(@Valid @RequestBody UpdateReq r) {
        long id = CurrentUser.require().id();
        db.sql("""
            UPDATE users SET full_name=COALESCE(:n, full_name), phone=COALESCE(:p, phone), language=COALESCE(:l, language),
              city=COALESCE(:c, city), notify_email=COALESCE(:ne, notify_email), notify_push=COALESCE(:np, notify_push), updated_at=now()
            WHERE id=:id""").param("n", r.fullName()).param("p", r.phone()).param("l", r.language()).param("c", r.city())
            .param("ne", r.notifyEmail()).param("np", r.notifyPush()).param("id", id).update();
        return UserRepository.get(db, id);
    }

    @PostMapping("/password")
    @Transactional
    public void password(@Valid @RequestBody PasswordReq r) {
        long id = CurrentUser.require().id();
        AuthService.checkPassword(r.newPassword());
        String hash = db.sql("SELECT password_hash FROM users WHERE id=:i").param("i", id).query(String.class).single();
        if (!encoder.matches(r.currentPassword(), hash)) throw ApiException.bad("BAD_CREDENTIALS", "Current password is incorrect");
        db.sql("UPDATE users SET password_hash=:p, updated_at=now() WHERE id=:i").param("p", encoder.encode(r.newPassword())).param("i", id).update();
        db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=:u AND revoked_at IS NULL").param("u", id).update();
    }

    /** Data export (privacy): everything we hold that belongs to this user. */
    @GetMapping("/export")
    public Map<String, Object> export() {
        long id = CurrentUser.require().id();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", UserRepository.get(db, id));
        out.put("bookings", db.sql("SELECT reference, service_name, start_at, end_at, status, price_minor, discount_minor, currency, payment_status, customer_note, created_at FROM bookings WHERE customer_id=:i ORDER BY start_at").param("i", id).query().listOfRows());
        out.put("reviews", db.sql("SELECT booking_id, rating, body, created_at FROM reviews WHERE customer_id=:i").param("i", id).query().listOfRows());
        out.put("favorites", db.sql("SELECT kind, target_id, created_at FROM favorites WHERE user_id=:i").param("i", id).query().listOfRows());
        out.put("notifications", db.sql("SELECT type, title, body, created_at FROM notifications WHERE user_id=:i").param("i", id).query().listOfRows());
        return out;
    }

    /** Account deletion: PII is erased and the account closed; bookings stay (anonymised) for the businesses' financial records. */
    @DeleteMapping
    @Transactional
    public void delete() {
        long id = CurrentUser.require().id();
        long upcoming = db.sql("SELECT count(*) FROM bookings WHERE customer_id=:i AND status IN ('PENDING','CONFIRMED') AND start_at > now()").param("i", id).query(Long.class).single();
        if (upcoming > 0) throw ApiException.unprocessable("HAS_UPCOMING_BOOKINGS", "Cancel upcoming bookings before deleting the account");
        long owned = db.sql("SELECT count(*) FROM businesses WHERE owner_id=:i").param("i", id).query(Long.class).single();
        if (owned > 0) throw ApiException.unprocessable("OWNS_BUSINESS", "Business owners must contact support to close their account");
        db.sql("UPDATE users SET email=:e, full_name='Deleted user', phone=NULL, city=NULL, status='DELETED', password_hash=:p, updated_at=now() WHERE id=:i")
          .param("e", "deleted-" + id + "@deleted.invalid").param("p", encoder.encode(java.util.UUID.randomUUID().toString())).param("i", id).update();
        db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=:u").param("u", id).update();
        db.sql("DELETE FROM favorites WHERE user_id=:u").param("u", id).update();
        db.sql("DELETE FROM notifications WHERE user_id=:u").param("u", id).update();
        db.sql("UPDATE reviews SET body='' WHERE customer_id=:u").param("u", id).update();
        db.sql("UPDATE bookings SET customer_note=NULL WHERE customer_id=:u").param("u", id).update();
        db.sql("DELETE FROM business_customer_notes WHERE customer_id=:u").param("u", id).update();
    }
}
