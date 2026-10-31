package com.sulu.engagement;

import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Reviews (create / report / reply) and favorites. */
@RestController
@RequestMapping("/api")
public class EngagementController {
    private final ReviewService reviews;
    private final JdbcClient db;
    public EngagementController(ReviewService reviews, JdbcClient db) { this.reviews = reviews; this.db = db; }

    public record ReviewReq(@Min(1) @Max(5) int rating, @Size(max = 2000) String body) {}
    public record ReportReq(@Size(max = 200) String reason) {}
    public record ReplyReq(@NotBlank @Size(max = 1000) String reply) {}

    @PostMapping("/bookings/{id}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> review(@PathVariable long id, @Valid @RequestBody ReviewReq r) {
        return Map.of("id", reviews.create(CurrentUser.require().id(), id, r.rating(), r.body()));
    }

    @PostMapping("/reviews/{id}/report")
    public void report(@PathVariable long id, @Valid @RequestBody(required = false) ReportReq r) { reviews.report(CurrentUser.require().id(), id, r == null ? null : r.reason()); }

    @PostMapping("/reviews/{id}/reply")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public void reply(@PathVariable long id, @Valid @RequestBody ReplyReq r) { reviews.reply(CurrentUser.require().id(), id, r.reply()); }

    // ---------------- favorites ----------------
    private static String kind(String k) {
        return switch (k.toLowerCase()) { case "business", "businesses" -> "BUSINESS"; case "professional", "professionals" -> "PROFESSIONAL"; case "service", "services" -> "SERVICE";
            default -> throw ApiException.bad("BAD_KIND", "Unknown favorite kind"); };
    }

    @PutMapping("/favorites/{kind}/{id}")
    public void add(@PathVariable String kind, @PathVariable long id) {
        String k = kind(kind);
        String table = switch (k) { case "BUSINESS" -> "businesses WHERE status='APPROVED'"; case "PROFESSIONAL" -> "professionals WHERE active"; default -> "services WHERE active"; };
        if (db.sql("SELECT count(*) FROM " + table + (table.contains("WHERE") ? " AND" : " WHERE") + " id=:i").param("i", id).query(Long.class).single() == 0) throw ApiException.notFound("Target");
        db.sql("INSERT INTO favorites(user_id, kind, target_id) VALUES (:u,:k,:t) ON CONFLICT DO NOTHING").param("u", CurrentUser.require().id()).param("k", k).param("t", id).update();
    }

    @DeleteMapping("/favorites/{kind}/{id}")
    public void remove(@PathVariable String kind, @PathVariable long id) {
        db.sql("DELETE FROM favorites WHERE user_id=:u AND kind=:k AND target_id=:t").param("u", CurrentUser.require().id()).param("k", kind(kind)).param("t", id).update();
    }

    @GetMapping("/favorites")
    public Map<String, Object> list() {
        long u = CurrentUser.require().id();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("businesses", Rows.rows(db.sql("""
            SELECT b.id, b.name, b.kind, b.city, b.address, b.rating_avg, b.rating_count, b.image_key, b.is_demo, TRUE AS favorite
            FROM favorites f JOIN businesses b ON b.id=f.target_id WHERE f.user_id=:u AND f.kind='BUSINESS' AND b.status='APPROVED' ORDER BY f.created_at DESC""").param("u", u).query().listOfRows()));
        out.put("professionals", Rows.rows(db.sql("""
            SELECT p.id, p.full_name, p.title, p.avatar_key, p.rating_avg, p.rating_count, p.business_id, b.name AS business_name, TRUE AS favorite
            FROM favorites f JOIN professionals p ON p.id=f.target_id JOIN businesses b ON b.id=p.business_id WHERE f.user_id=:u AND f.kind='PROFESSIONAL' AND p.active AND b.status='APPROVED' ORDER BY f.created_at DESC""").param("u", u).query().listOfRows()));
        out.put("services", Rows.rows(db.sql("""
            SELECT s.id, s.name, s.price_minor, s.price_from, s.currency, s.duration_min, s.business_id, b.name AS business_name, TRUE AS favorite
            FROM favorites f JOIN services s ON s.id=f.target_id JOIN businesses b ON b.id=s.business_id WHERE f.user_id=:u AND f.kind='SERVICE' AND s.active AND b.status='APPROVED' ORDER BY f.created_at DESC""").param("u", u).query().listOfRows()));
        return out;
    }
}
