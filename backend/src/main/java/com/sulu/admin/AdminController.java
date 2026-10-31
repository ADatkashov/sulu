package com.sulu.admin;

import com.sulu.booking.BookingService;
import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import com.sulu.engagement.AuditService;
import com.sulu.engagement.NotificationService;
import com.sulu.engagement.ReviewService;
import com.sulu.payment.PaymentService;
import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Platform administration. Gated twice: URL rule (ROLE_PLATFORM_ADMIN) and @PreAuthorize. Role is read from the DB, never from the client. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {
    private final JdbcClient db; private final AuditService audit; private final NotificationService notifications;
    private final ReviewService reviews; private final PaymentService payments; private final BookingService bookings;

    public AdminController(JdbcClient db, AuditService audit, NotificationService notifications, ReviewService reviews, PaymentService payments, BookingService bookings) {
        this.db = db; this.audit = audit; this.notifications = notifications; this.reviews = reviews; this.payments = payments; this.bookings = bookings;
    }
    private long me() { return CurrentUser.require().id(); }
    private static int size(int s) { return Math.max(1, Math.min(s, 100)); }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("users", Rows.rows(db.sql("SELECT role, count(*) AS n FROM users WHERE status<>'DELETED' GROUP BY role ORDER BY role").query().listOfRows()));
        o.put("businesses", Rows.rows(db.sql("SELECT status, count(*) AS n FROM businesses GROUP BY status ORDER BY status").query().listOfRows()));
        o.put("bookings", Rows.rows(db.sql("SELECT status, count(*) AS n FROM bookings GROUP BY status ORDER BY status").query().listOfRows()));
        o.put("bookingsLast30Days", db.sql("SELECT count(*) FROM bookings WHERE created_at > now() - interval '30 days'").query(Long.class).single());
        o.put("gmvCompletedMinor", Rows.rows(db.sql("SELECT currency, COALESCE(sum(price_minor-discount_minor),0) AS total_minor, COALESCE(sum((price_minor-discount_minor)*commission_bps/10000),0) AS commission_minor FROM bookings WHERE status='COMPLETED' GROUP BY currency").query().listOfRows()));
        o.put("reportedReviews", db.sql("SELECT count(*) FROM reviews WHERE reported_count > 0 AND status='VISIBLE'").query(Long.class).single());
        o.put("pendingBusinesses", db.sql("SELECT count(*) FROM businesses WHERE status='PENDING_VERIFICATION'").query(Long.class).single());
        return o;
    }

    // ---------------- users
    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        size = size(size);
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        Map<String, Object> p = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) { w.append(" AND (lower(email) LIKE :q OR lower(full_name) LIKE :q)"); p.put("q", "%" + q.trim().toLowerCase() + "%"); }
        if (role != null && !role.isBlank()) { w.append(" AND role=:r"); p.put("r", role); }
        p.put("l", size); p.put("o", Math.max(0, page) * size);
        return Rows.rows(db.sql("SELECT id, email, full_name, phone, role, status, language, city, created_at FROM users" + w + " ORDER BY id DESC LIMIT :l OFFSET :o").params(p).query().listOfRows());
    }

    public record StatusReq(@Pattern(regexp = "ACTIVE|BLOCKED") String status, @Size(max = 200) String reason) {}
    @PostMapping("/users/{id}/status")
    @Transactional
    public void userStatus(@PathVariable long id, @Valid @RequestBody StatusReq r) {
        if (id == me()) throw ApiException.unprocessable("SELF_BLOCK", "You cannot change your own status");
        var u = db.sql("SELECT role, status FROM users WHERE id=:i FOR UPDATE").param("i", id).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
        if ("DELETED".equals(u.get("status"))) throw ApiException.conflict("DELETED", "User is deleted");
        db.sql("UPDATE users SET status=:s, updated_at=now() WHERE id=:i").param("s", r.status()).param("i", id).update();
        if ("BLOCKED".equals(r.status())) db.sql("UPDATE refresh_tokens SET revoked_at=now() WHERE user_id=:u AND revoked_at IS NULL").param("u", id).update();
        audit.log(me(), "USER_" + r.status(), "USER", id, r.reason());
    }

    // ---------------- businesses
    @GetMapping("/businesses")
    public List<Map<String, Object>> businesses(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        size = size(size);
        String w = status == null || status.isBlank() ? "" : " WHERE b.status=:s";
        var spec = db.sql("SELECT b.id, b.name, b.kind, b.city, b.address, b.status, b.is_demo, b.created_at, u.email AS owner_email, u.full_name AS owner_name, b.rating_avg, b.rating_count FROM businesses b JOIN users u ON u.id=b.owner_id" + w + " ORDER BY b.id DESC LIMIT :l OFFSET :o");
        if (!w.isEmpty()) spec = spec.param("s", status);
        return Rows.rows(spec.param("l", size).param("o", Math.max(0, page) * size).query().listOfRows());
    }

    public record VerifyReq(@Pattern(regexp = "APPROVED|REJECTED|SUSPENDED") String status, @Size(max = 200) String reason) {}
    @PostMapping("/businesses/{id}/status")
    @Transactional
    public void businessStatus(@PathVariable long id, @Valid @RequestBody VerifyReq r) {
        var b = db.sql("SELECT owner_id, name FROM businesses WHERE id=:i FOR UPDATE").param("i", id).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Business"));
        db.sql("UPDATE businesses SET status=:s, updated_at=now() WHERE id=:i").param("s", r.status()).param("i", id).update();
        audit.log(me(), "BUSINESS_" + r.status(), "BUSINESS", id, r.reason());
        notifications.notify(((Number) b.get("owner_id")).longValue(), "BUSINESS_STATUS", "Статус бизнеса: " + r.status(), b.get("name") + (r.reason() == null ? "" : " — " + r.reason()), "/business", "bizstatus:" + id + ":" + r.status() + ":" + System.nanoTime());
    }

    // ---------------- categories
    public record CategoryReq(@NotBlank @Pattern(regexp = "[a-z0-9-]{2,40}") String slug, @NotBlank @Size(max = 80) String nameKk, @NotBlank @Size(max = 80) String nameRu,
                              @NotBlank @Size(max = 80) String nameEn, @Size(max = 80) String nameTr, @Size(max = 40) String icon, int sortOrder, boolean active) {}

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() { return Rows.rows(db.sql("SELECT * FROM service_categories ORDER BY sort_order, id").query().listOfRows()); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@Valid @RequestBody CategoryReq r) {
        try {
            long id = db.sql("INSERT INTO service_categories(slug, name_kk, name_ru, name_en, name_tr, icon, sort_order, active) VALUES (:s,:k,:r,:e,:t,:i,:o,:a) RETURNING id")
                    .param("s", r.slug()).param("k", r.nameKk()).param("r", r.nameRu()).param("e", r.nameEn()).param("t", r.nameTr()).param("i", r.icon() == null ? "sparkles" : r.icon()).param("o", r.sortOrder()).param("a", r.active()).query(Long.class).single();
            audit.log(me(), "CATEGORY_CREATED", "CATEGORY", id, r.slug());
            return Map.of("id", id);
        } catch (DuplicateKeyException e) { throw ApiException.conflict("SLUG_TAKEN", "Slug already exists"); }
    }

    @PutMapping("/categories/{id}")
    public void updateCategory(@PathVariable long id, @Valid @RequestBody CategoryReq r) {
        int n = db.sql("UPDATE service_categories SET name_kk=:k, name_ru=:r, name_en=:e, name_tr=:t, icon=COALESCE(:i, icon), sort_order=:o, active=:a WHERE id=:id")
                .param("k", r.nameKk()).param("r", r.nameRu()).param("e", r.nameEn()).param("t", r.nameTr()).param("i", r.icon()).param("o", r.sortOrder()).param("a", r.active()).param("id", id).update();
        if (n == 0) throw ApiException.notFound("Category");
        audit.log(me(), "CATEGORY_UPDATED", "CATEGORY", id, r.slug());
    }

    // ---------------- reviews moderation
    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(defaultValue = "reported") String filter) {
        String w = filter.equals("all") ? "" : "WHERE r.reported_count > 0";
        return Rows.rows(db.sql("SELECT r.id, r.rating, r.body, r.status, r.reported_count, r.created_at, u.full_name AS author, b.name AS business_name FROM reviews r JOIN users u ON u.id=r.customer_id JOIN businesses b ON b.id=r.business_id " + w + " ORDER BY r.reported_count DESC, r.created_at DESC LIMIT 100").query().listOfRows());
    }

    public record ModerateReq(@Pattern(regexp = "VISIBLE|HIDDEN") String status) {}
    @PostMapping("/reviews/{id}/status")
    public void moderate(@PathVariable long id, @Valid @RequestBody ModerateReq r) {
        reviews.setStatus(id, r.status());
        audit.log(me(), "REVIEW_" + r.status(), "REVIEW", id, null);
    }

    // ---------------- bookings & payments
    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings(@RequestParam(required = false) String status, @RequestParam(required = false) String reference, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        size = size(size);
        StringBuilder w = new StringBuilder(" WHERE 1=1"); Map<String, Object> p = new LinkedHashMap<>();
        if (status != null && !status.isBlank()) { w.append(" AND bk.status=:s"); p.put("s", status); }
        if (reference != null && !reference.isBlank()) { w.append(" AND bk.reference=:ref"); p.put("ref", reference.trim().toUpperCase()); }
        p.put("l", size); p.put("o", Math.max(0, page) * size);
        return Rows.rows(db.sql("SELECT bk.id, bk.reference, bk.status, bk.start_at, bk.price_minor, bk.discount_minor, bk.currency, bk.payment_status, bk.service_name, b.name AS business_name, u.email AS customer_email FROM bookings bk JOIN businesses b ON b.id=bk.business_id JOIN users u ON u.id=bk.customer_id" + w + " ORDER BY bk.id DESC LIMIT :l OFFSET :o").params(p).query().listOfRows());
    }

    @GetMapping("/bookings/{id}")
    public Map<String, Object> booking(@PathVariable long id) {
        var b = Rows.row(db.sql("SELECT bk.*, b.name AS business_name, u.email AS customer_email FROM bookings bk JOIN businesses b ON b.id=bk.business_id JOIN users u ON u.id=bk.customer_id WHERE bk.id=:i").param("i", id).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Booking")));
        b.remove("during");
        b.put("history", Rows.rows(db.sql("SELECT from_status, to_status, actor_id, note, created_at FROM booking_status_history WHERE booking_id=:i ORDER BY id").param("i", id).query().listOfRows()));
        b.put("payments", Rows.rows(db.sql("SELECT id, provider, provider_ref, method, amount_minor, refunded_minor, currency, status, simulated, created_at FROM payments WHERE booking_id=:i ORDER BY id").param("i", id).query().listOfRows()));
        return b;
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> payments(@RequestParam(required = false) String status) {
        String w = status == null || status.isBlank() ? "" : " WHERE p.status=:s";
        var spec = db.sql("SELECT p.id, p.booking_id, bk.reference, p.provider, p.method, p.amount_minor, p.refunded_minor, p.currency, p.status, p.simulated, p.created_at FROM payments p JOIN bookings bk ON bk.id=p.booking_id" + w + " ORDER BY p.id DESC LIMIT 100");
        if (!w.isEmpty()) spec = spec.param("s", status);
        return Rows.rows(spec.query().listOfRows()); // provider credentials are never stored in or returned from this table
    }

    public record RefundReq(@Min(1) Long amountMinor) {}
    @PostMapping("/payments/{id}/refund")
    public Map<String, Object> refund(@PathVariable long id, @RequestBody(required = false) RefundReq r) { return payments.refund(id, r == null ? null : r.amountMinor(), me()); }

    @PostMapping("/bookings/{id}/cancel")
    public Map<String, Object> cancelBooking(@PathVariable long id, @RequestBody(required = false) Map<String, String> body) {
        return bookings.act(new BookingService.Actor(me(), "PLATFORM_ADMIN"), id, "cancel", body == null ? null : body.get("reason"));
    }

    // ---------------- settings, plans, promotions
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        db.sql("SELECT key, value FROM platform_settings ORDER BY key").query().listOfRows().forEach(r -> m.put((String) r.get("key"), r.get("value")));
        return m;
    }

    @PutMapping("/settings/{key}")
    public void setting(@PathVariable String key, @RequestBody Map<String, String> body) {
        String v = body.get("value");
        if (v == null || v.isBlank() || v.length() > 200) throw ApiException.bad("BAD_VALUE", "value is required");
        switch (key) {
            case "commission_bps" -> { try { int n = Integer.parseInt(v); if (n < 0 || n > 5000) throw new NumberFormatException(); } catch (NumberFormatException e) { throw ApiException.bad("BAD_VALUE", "commission_bps must be 0..5000"); } }
            case "default_city", "default_currency", "default_timezone" -> {}
            default -> throw ApiException.notFound("Setting");
        }
        db.sql("UPDATE platform_settings SET value=:v, updated_at=now() WHERE key=:k").param("v", v).param("k", key).update();
        audit.log(me(), "SETTING_CHANGED", "SETTING", key, v);
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> plans() { return Rows.rows(db.sql("SELECT id, code, name, price_minor, currency, features, active FROM subscription_plans ORDER BY id").query().listOfRows()); }

    public record PlanReq(@Min(0) long priceMinor, boolean active, @Size(max = 400) String features) {}
    @PutMapping("/plans/{id}")
    public void plan(@PathVariable long id, @Valid @RequestBody PlanReq r) {
        int n = db.sql("UPDATE subscription_plans SET price_minor=:p, active=:a, features=COALESCE(:f, features) WHERE id=:i").param("p", r.priceMinor()).param("a", r.active()).param("f", r.features()).param("i", id).update();
        if (n == 0) throw ApiException.notFound("Plan");
        audit.log(me(), "PLAN_UPDATED", "PLAN", id, "priceMinor=" + r.priceMinor());
    }

    public record PlatformPromoReq(@NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code, @Min(1) @Max(100) Integer percentOff, @Min(1) Long amountOffMinor,
                                   @Size(min = 3, max = 3) String currency, Instant validTo, @Min(1) Integer maxRedemptions) {}

    @GetMapping("/promotions")
    public List<Map<String, Object>> promotions() { return Rows.rows(db.sql("SELECT id, code, business_id, percent_off, amount_off_minor, currency, valid_from, valid_to, max_redemptions, redemptions, active FROM promotions ORDER BY id DESC LIMIT 100").query().listOfRows()); }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPromo(@Valid @RequestBody PlatformPromoReq r) {
        if ((r.percentOff() == null) == (r.amountOffMinor() == null)) throw ApiException.bad("BAD_PROMO", "Provide exactly one of percentOff or amountOffMinor");
        if (r.amountOffMinor() != null && r.currency() == null) throw ApiException.bad("BAD_PROMO", "currency is required for fixed discounts");
        try {
            long id = db.sql("INSERT INTO promotions(code, percent_off, amount_off_minor, currency, valid_to, max_redemptions) VALUES (:c,:p,:a,:cur,:v,:m) RETURNING id")
                    .param("c", r.code().toUpperCase()).param("p", r.percentOff()).param("a", r.amountOffMinor()).param("cur", r.currency()).param("v", r.validTo() == null ? null : Timestamp.from(r.validTo())).param("m", r.maxRedemptions()).query(Long.class).single();
            audit.log(me(), "PROMO_CREATED", "PROMOTION", id, r.code());
            return Map.of("id", id);
        } catch (DuplicateKeyException e) { throw ApiException.conflict("PROMO_CODE_TAKEN", "Code already exists"); }
    }

    @PostMapping("/promotions/{id}/active")
    public void promoActive(@PathVariable long id, @RequestParam boolean value) {
        db.sql("UPDATE promotions SET active=:a WHERE id=:i").param("a", value).param("i", id).update();
        audit.log(me(), value ? "PROMO_ENABLED" : "PROMO_DISABLED", "PROMOTION", id, null);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        size = size(size);
        return Rows.rows(db.sql("SELECT a.id, a.action, a.target_type, a.target_id, a.detail, a.created_at, u.email AS actor_email FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT :l OFFSET :o").param("l", size).param("o", Math.max(0, page) * size).query().listOfRows());
    }
}
