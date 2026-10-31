package com.sulu.business;

import com.sulu.booking.BookingService;
import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import com.sulu.engagement.AuditService;
import com.sulu.engagement.NotificationService;
import com.sulu.security.CurrentUser;
import com.sulu.security.JwtService;
import com.sulu.storage.StorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Business owner back-office. All /api/biz/{businessId}/** routes are tenant-scoped through BusinessAccess. */
@RestController
@RequestMapping("/api")
public class BusinessController {
    private static final Logger log = LoggerFactory.getLogger(BusinessController.class);
    private final JdbcClient db;
    private final BusinessAccess access;
    private final BookingService bookings;
    private final StaffService staff;
    private final StorageService storage;
    private final AuditService audit;
    private final JwtService jwt;
    private final Environment env;
    private final Clock clock;

    public BusinessController(JdbcClient db, BusinessAccess access, BookingService bookings, StaffService staff, StorageService storage,
                              AuditService audit, JwtService jwt, Environment env, Clock clock) {
        this.db = db; this.access = access; this.bookings = bookings; this.staff = staff; this.storage = storage; this.audit = audit; this.jwt = jwt; this.env = env; this.clock = clock;
    }

    // ============================================================ business profile
    public record BusinessReq(@NotBlank @Size(max = 120) String name, @Pattern(regexp = "SALON|STUDIO|BARBERSHOP|INDEPENDENT") String kind,
                              @Size(max = 4000) String description, @NotBlank @Size(max = 80) String city, @NotBlank @Size(max = 200) String address,
                              @Size(max = 32) String phone, Double lat, Double lng, String timezone) {}
    public record RulesReq(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description, @NotBlank @Size(max = 200) String address,
                           @Size(max = 32) String phone, Double lat, Double lng, boolean autoConfirm,
                           @Min(0) @Max(10080) int minLeadMinutes, @Min(1) @Max(365) int maxAdvanceDays, @Min(0) @Max(120) int bufferMinutes,
                           @Min(0) @Max(720) int cancellationHours) {}
    public record HourReq(@Min(1) @Max(7) int weekday, @NotBlank String openTime, @NotBlank String closeTime) {}

    @PostMapping("/business")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody BusinessReq r) {
        long owner = CurrentUser.require().id();
        String tz = r.timezone() == null ? db.sql("SELECT value FROM platform_settings WHERE key='default_timezone'").query(String.class).optional().orElse("Asia/Almaty") : r.timezone();
        try { ZoneId.of(tz); } catch (Exception e) { throw ApiException.bad("BAD_TIMEZONE", "Unknown time zone"); }
        String cur = db.sql("SELECT value FROM platform_settings WHERE key='default_currency'").query(String.class).optional().orElse("KZT");
        long id = db.sql("""
            INSERT INTO businesses(owner_id, name, kind, description, city, address, lat, lng, phone, timezone, currency)
            VALUES (:o,:n,:k,:d,:c,:a,:la,:ln,:p,:tz,:cur) RETURNING id""")
            .param("o", owner).param("n", r.name().trim()).param("k", r.kind() == null ? "SALON" : r.kind()).param("d", r.description() == null ? "" : r.description())
            .param("c", r.city().trim()).param("a", r.address().trim()).param("la", r.lat()).param("ln", r.lng()).param("p", r.phone()).param("tz", tz).param("cur", cur)
            .query(Long.class).single();
        for (int d = 1; d <= 6; d++)
            db.sql("INSERT INTO business_hours(business_id, weekday, open_time, close_time) VALUES (:b,:w,'10:00','20:00')").param("b", id).param("w", d).update();
        db.sql("INSERT INTO business_subscriptions(business_id, plan_id) SELECT :b, id FROM subscription_plans WHERE code='FREE'").param("b", id).update();
        audit.log(owner, "BUSINESS_CREATED", "BUSINESS", id, r.name());
        return Map.of("id", id, "status", "PENDING_VERIFICATION");
    }

    @GetMapping("/business")
    public List<Map<String, Object>> mine() {
        return Rows.rows(db.sql("SELECT id, name, kind, city, address, status, is_demo, rating_avg, rating_count FROM businesses WHERE owner_id=:o ORDER BY id").param("o", CurrentUser.require().id()).query().listOfRows());
    }

    @GetMapping("/biz/{bid}")
    public Map<String, Object> settings(@PathVariable long bid) {
        access.owned(bid);
        var b = Rows.row(db.sql("""
            SELECT id, name, kind, description, city, address, lat, lng, phone, timezone, currency, status, is_demo, auto_confirm, min_lead_minutes,
                   max_advance_days, buffer_minutes, cancellation_hours, rating_avg, rating_count FROM businesses WHERE id=:i""").param("i", bid).query().listOfRows().get(0));
        b.put("hours", Rows.rows(db.sql("SELECT weekday, open_time, close_time FROM business_hours WHERE business_id=:i ORDER BY weekday").param("i", bid).query().listOfRows()));
        b.put("closures", Rows.rows(db.sql("SELECT id, closed_on, reason FROM business_closures WHERE business_id=:i AND closed_on >= current_date ORDER BY closed_on").param("i", bid).query().listOfRows()));
        b.put("subscription", Rows.row(db.sql("SELECT p.code, p.name, p.price_minor, p.currency, s.status FROM business_subscriptions s JOIN subscription_plans p ON p.id=s.plan_id WHERE s.business_id=:i").param("i", bid).query().listOfRows().stream().findFirst().orElse(Map.of())));
        return b;
    }

    @PutMapping("/biz/{bid}")
    public Map<String, Object> updateSettings(@PathVariable long bid, @Valid @RequestBody RulesReq r) {
        access.owned(bid);
        db.sql("""
            UPDATE businesses SET name=:n, description=:d, address=:a, phone=:p, lat=:la, lng=:ln, auto_confirm=:ac, min_lead_minutes=:ml,
              max_advance_days=:ma, buffer_minutes=:bf, cancellation_hours=:ch, updated_at=now() WHERE id=:i""")
            .param("n", r.name().trim()).param("d", r.description() == null ? "" : r.description()).param("a", r.address().trim()).param("p", r.phone()).param("la", r.lat()).param("ln", r.lng())
            .param("ac", r.autoConfirm()).param("ml", r.minLeadMinutes()).param("ma", r.maxAdvanceDays()).param("bf", r.bufferMinutes()).param("ch", r.cancellationHours()).param("i", bid).update();
        return settings(bid);
    }

    @PutMapping("/biz/{bid}/hours")
    @Transactional
    public void setHours(@PathVariable long bid, @RequestBody List<@Valid HourReq> hours) {
        access.owned(bid);
        Set<Integer> seen = new HashSet<>();
        for (HourReq h : hours) {
            if (!seen.add(h.weekday())) throw ApiException.bad("BAD_WEEKDAY", "weekday must be unique");
            LocalTime o, c;
            try { o = LocalTime.parse(h.openTime()); c = LocalTime.parse(h.closeTime()); } catch (Exception e) { throw ApiException.bad("BAD_TIME", "Times must be HH:mm"); }
            if (!c.isAfter(o)) throw ApiException.bad("BAD_HOURS", "closeTime must be after openTime");
        }
        db.sql("DELETE FROM business_hours WHERE business_id=:b").param("b", bid).update();
        for (HourReq h : hours)
            db.sql("INSERT INTO business_hours(business_id, weekday, open_time, close_time) VALUES (:b,:w,:o,:c)").param("b", bid).param("w", h.weekday())
              .param("o", java.sql.Time.valueOf(LocalTime.parse(h.openTime()).withSecond(0))).param("c", java.sql.Time.valueOf(LocalTime.parse(h.closeTime()).withSecond(0))).update();
    }

    public record ClosureReq(@NotNull LocalDate date, @Size(max = 120) String reason) {}
    @PostMapping("/biz/{bid}/closures")
    public void addClosure(@PathVariable long bid, @Valid @RequestBody ClosureReq r) {
        access.owned(bid);
        db.sql("INSERT INTO business_closures(business_id, closed_on, reason) VALUES (:b,:d,:r) ON CONFLICT DO NOTHING").param("b", bid).param("d", java.sql.Date.valueOf(r.date())).param("r", r.reason()).update();
    }
    @DeleteMapping("/biz/{bid}/closures/{id}")
    public void removeClosure(@PathVariable long bid, @PathVariable long id) {
        access.owned(bid);
        db.sql("DELETE FROM business_closures WHERE id=:i AND business_id=:b").param("i", id).param("b", bid).update();
    }

    // ============================================================ services
    public record ServiceReq(@NotNull Long categoryId, @NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                             @Min(0) @Max(1_000_000_000L) long priceMinor, boolean priceFrom, @Min(5) @Max(720) int durationMin, boolean atHome,
                             @Size(max = 200) String tags, List<Long> professionalIds) {}

    @GetMapping("/biz/{bid}/services")
    public List<Map<String, Object>> services(@PathVariable long bid) {
        access.owned(bid);
        var list = Rows.rows(db.sql("""
            SELECT s.id, s.category_id, c.name_ru AS category_name, s.name, s.description, s.price_minor, s.price_from, s.currency, s.duration_min, s.at_home, s.tags, s.active, s.image_key
            FROM services s JOIN service_categories c ON c.id=s.category_id WHERE s.business_id=:b ORDER BY s.active DESC, s.name""").param("b", bid).query().listOfRows());
        for (var s : list) s.put("professionalIds", db.sql("SELECT professional_id FROM professional_services WHERE service_id=:s").param("s", s.get("id")).query(Long.class).list());
        return list;
    }

    @PostMapping("/biz/{bid}/services")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createService(@PathVariable long bid, @Valid @RequestBody ServiceReq r) {
        var biz = access.owned(bid);
        checkCategory(r.categoryId());
        long id = db.sql("""
            INSERT INTO services(business_id, category_id, name, description, price_minor, price_from, currency, duration_min, at_home, tags)
            VALUES (:b,:c,:n,:d,:p,:pf,:cur,:du,:ah,:t) RETURNING id""")
            .param("b", bid).param("c", r.categoryId()).param("n", r.name().trim()).param("d", r.description() == null ? "" : r.description()).param("p", r.priceMinor())
            .param("pf", r.priceFrom()).param("cur", biz.currency()).param("du", r.durationMin()).param("ah", r.atHome()).param("t", r.tags() == null ? "" : r.tags()).query(Long.class).single();
        assign(bid, id, r.professionalIds());
        return Map.of("id", id);
    }

    @PutMapping("/biz/{bid}/services/{sid}")
    @Transactional
    public void updateService(@PathVariable long bid, @PathVariable long sid, @Valid @RequestBody ServiceReq r) {
        access.owned(bid); access.serviceInBusiness(bid, sid); checkCategory(r.categoryId());
        db.sql("UPDATE services SET category_id=:c, name=:n, description=:d, price_minor=:p, price_from=:pf, duration_min=:du, at_home=:ah, tags=:t, updated_at=now() WHERE id=:i")
          .param("c", r.categoryId()).param("n", r.name().trim()).param("d", r.description() == null ? "" : r.description()).param("p", r.priceMinor()).param("pf", r.priceFrom())
          .param("du", r.durationMin()).param("ah", r.atHome()).param("t", r.tags() == null ? "" : r.tags()).param("i", sid).update();
        if (r.professionalIds() != null) { db.sql("DELETE FROM professional_services WHERE service_id=:s").param("s", sid).update(); assign(bid, sid, r.professionalIds()); }
    }

    /** Services are archived, never hard-deleted, so historical bookings keep their reference. */
    @PostMapping("/biz/{bid}/services/{sid}/active")
    public void setServiceActive(@PathVariable long bid, @PathVariable long sid, @RequestParam boolean value) {
        access.owned(bid); access.serviceInBusiness(bid, sid);
        db.sql("UPDATE services SET active=:a, updated_at=now() WHERE id=:i").param("a", value).param("i", sid).update();
    }

    @PostMapping("/biz/{bid}/services/{sid}/image")
    public Map<String, Object> serviceImage(@PathVariable long bid, @PathVariable long sid, @RequestParam("file") MultipartFile file) {
        access.owned(bid); access.serviceInBusiness(bid, sid);
        String key = storage.storeImage(file);
        db.sql("UPDATE services SET image_key=:k WHERE id=:i").param("k", key).param("i", sid).update();
        return Map.of("imageKey", key);
    }

    private void checkCategory(long id) {
        if (db.sql("SELECT count(*) FROM service_categories WHERE id=:i AND active").param("i", id).query(Long.class).single() == 0) throw ApiException.bad("BAD_CATEGORY", "Unknown category");
    }
    private void assign(long bid, long serviceId, List<Long> pros) {
        if (pros == null) return;
        for (long p : new LinkedHashSet<>(pros)) {
            access.professionalInBusiness(bid, p);
            db.sql("INSERT INTO professional_services(professional_id, service_id) VALUES (:p,:s) ON CONFLICT DO NOTHING").param("p", p).param("s", serviceId).update();
        }
    }

    // ============================================================ staff
    public record ProReq(@NotBlank @Size(max = 120) String fullName, @Size(max = 120) String title, @Size(max = 4000) String bio, @Min(0) @Max(80) int experienceYears,
                         @Size(max = 300) String specialties, @Pattern(regexp = "FEMALE|MALE") String gender, List<Long> serviceIds) {}
    public record TimeOffReq(@NotNull Instant from, @NotNull Instant to, @Size(max = 120) String reason) {}
    public record InviteReq(@NotBlank @Email String email) {}

    @GetMapping("/biz/{bid}/professionals")
    public List<Map<String, Object>> professionals(@PathVariable long bid) {
        access.owned(bid);
        var list = Rows.rows(db.sql("SELECT id, full_name, title, bio, experience_years, specialties, gender, avatar_key, active, rating_avg, rating_count, user_id IS NOT NULL AS has_account FROM professionals WHERE business_id=:b ORDER BY active DESC, full_name").param("b", bid).query().listOfRows());
        for (var p : list) {
            p.put("serviceIds", db.sql("SELECT service_id FROM professional_services WHERE professional_id=:p").param("p", p.get("id")).query(Long.class).list());
            p.put("hours", staff.hours(((Number) p.get("id")).longValue()));
            p.put("timeOff", staff.timeOff(((Number) p.get("id")).longValue()));
            p.put("portfolio", Rows.rows(db.sql("SELECT id, image_key, caption FROM portfolio_images WHERE professional_id=:p ORDER BY id DESC").param("p", p.get("id")).query().listOfRows()));
        }
        return list;
    }

    @PostMapping("/biz/{bid}/professionals")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addProfessional(@PathVariable long bid, @Valid @RequestBody ProReq r) {
        access.owned(bid);
        long id = db.sql("INSERT INTO professionals(business_id, full_name, title, bio, experience_years, specialties, gender) VALUES (:b,:n,:t,:bio,:e,:s,:g) RETURNING id")
                .param("b", bid).param("n", r.fullName().trim()).param("t", nz(r.title())).param("bio", nz(r.bio())).param("e", r.experienceYears()).param("s", nz(r.specialties())).param("g", r.gender()).query(Long.class).single();
        if (r.serviceIds() != null) for (long s : new LinkedHashSet<>(r.serviceIds())) { access.serviceInBusiness(bid, s); db.sql("INSERT INTO professional_services VALUES (:p,:s)").param("p", id).param("s", s).update(); }
        // sensible default schedule: Mon–Sat 10:00–19:00 with a lunch break; editable immediately
        staff.setHours(id, java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(d -> new StaffService.Hours(d, "10:00", "19:00", "13:00", "14:00")).toList());
        return Map.of("id", id);
    }

    @PutMapping("/biz/{bid}/professionals/{pid}")
    @Transactional
    public void updateProfessional(@PathVariable long bid, @PathVariable long pid, @Valid @RequestBody ProReq r) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        db.sql("UPDATE professionals SET full_name=:n, title=:t, bio=:b, experience_years=:e, specialties=:s, gender=:g WHERE id=:i")
          .param("n", r.fullName().trim()).param("t", nz(r.title())).param("b", nz(r.bio())).param("e", r.experienceYears()).param("s", nz(r.specialties())).param("g", r.gender()).param("i", pid).update();
        if (r.serviceIds() != null) {
            db.sql("DELETE FROM professional_services WHERE professional_id=:p").param("p", pid).update();
            for (long s : new LinkedHashSet<>(r.serviceIds())) { access.serviceInBusiness(bid, s); db.sql("INSERT INTO professional_services VALUES (:p,:s)").param("p", pid).param("s", s).update(); }
        }
    }

    @PostMapping("/biz/{bid}/professionals/{pid}/active")
    public void setActive(@PathVariable long bid, @PathVariable long pid, @RequestParam boolean value) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        db.sql("UPDATE professionals SET active=:a WHERE id=:i").param("a", value).param("i", pid).update();
    }

    @PutMapping("/biz/{bid}/professionals/{pid}/hours")
    public void setProHours(@PathVariable long bid, @PathVariable long pid, @RequestBody List<StaffService.Hours> hours) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        staff.setHours(pid, hours);
    }

    @PostMapping("/biz/{bid}/professionals/{pid}/time-off")
    public Map<String, Object> addTimeOff(@PathVariable long bid, @PathVariable long pid, @Valid @RequestBody TimeOffReq r) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        return staff.addTimeOff(pid, r.from(), r.to(), r.reason());
    }

    @DeleteMapping("/biz/{bid}/professionals/{pid}/time-off/{id}")
    public void removeTimeOff(@PathVariable long bid, @PathVariable long pid, @PathVariable long id) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        staff.removeTimeOff(pid, id);
    }

    @PostMapping("/biz/{bid}/professionals/{pid}/portfolio")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addPortfolio(@PathVariable long bid, @PathVariable long pid, @RequestParam("file") MultipartFile file, @RequestParam(required = false) String caption) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        if (db.sql("SELECT count(*) FROM portfolio_images WHERE professional_id=:p").param("p", pid).query(Long.class).single() >= 30) throw ApiException.unprocessable("PORTFOLIO_FULL", "Portfolio limit reached");
        String key = storage.storeImage(file);
        long id = db.sql("INSERT INTO portfolio_images(professional_id, image_key, caption) VALUES (:p,:k,:c) RETURNING id").param("p", pid).param("k", key).param("c", caption == null ? null : caption.substring(0, Math.min(caption.length(), 160))).query(Long.class).single();
        return Map.of("id", id, "imageKey", key);
    }

    @PostMapping("/biz/{bid}/professionals/{pid}/avatar")
    public Map<String, Object> avatar(@PathVariable long bid, @PathVariable long pid, @RequestParam("file") MultipartFile file) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        String key = storage.storeImage(file);
        db.sql("UPDATE professionals SET avatar_key=:k WHERE id=:i").param("k", key).param("i", pid).update();
        return Map.of("imageKey", key);
    }

    @DeleteMapping("/biz/{bid}/professionals/{pid}/portfolio/{id}")
    public void removePortfolio(@PathVariable long bid, @PathVariable long pid, @PathVariable long id) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        db.sql("DELETE FROM portfolio_images WHERE id=:i AND professional_id=:p").param("i", id).param("p", pid).update();
    }

    /** Invite a professional by email. With no email provider configured the link is only logged in the dev profile — never claimed as sent. */
    @PostMapping("/biz/{bid}/professionals/{pid}/invite")
    public Map<String, Object> invite(@PathVariable long bid, @PathVariable long pid, @Valid @RequestBody InviteReq r) {
        access.owned(bid); access.professionalInBusiness(bid, pid);
        String token = jwt.newOpaqueToken();
        db.sql("INSERT INTO professional_invites(professional_id, email, token_hash, expires_at) VALUES (:p,:e,:h,:x)")
          .param("p", pid).param("e", r.email().trim()).param("h", JwtService.hash(token)).param("x", Timestamp.from(clock.instant().plus(Duration.ofDays(7)))).update();
        boolean dev = env.acceptsProfiles(Profiles.of("dev"));
        if (dev) log.warn("DEV ONLY invite token for {}: {}", r.email(), token);
        return Map.of("delivered", false, "note", "No email provider is configured; share the invitation link manually.", "devToken", dev ? token : "");
    }

    // ============================================================ bookings
    @GetMapping("/biz/{bid}/bookings")
    public List<Map<String, Object>> calendar(@PathVariable long bid, @RequestParam LocalDate from, @RequestParam LocalDate to,
                                              @RequestParam(required = false) Long professionalId, @RequestParam(required = false) String status) {
        var biz = access.owned(bid);
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) throw ApiException.bad("BAD_RANGE", "Range must be 1..62 days");
        return bookings.listForBusiness(bid, professionalId, from, to, status, biz.zone());
    }

    @GetMapping("/biz/{bid}/bookings/{id}")
    public Map<String, Object> booking(@PathVariable long bid, @PathVariable long id) { access.owned(bid); return bookings.viewForBusiness(id, bid); }

    public record ActionReq(@Size(max = 200) String reason) {}
    public record RescheduleReq(@NotNull Instant startAt) {}

    @PostMapping("/biz/{bid}/bookings/{id}/{action}")
    public Map<String, Object> action(@PathVariable long bid, @PathVariable long id, @PathVariable String action, @RequestBody(required = false) ActionReq r) {
        access.owned(bid); bookings.viewForBusiness(id, bid);
        var u = CurrentUser.require();
        if (action.equals("reschedule")) throw ApiException.bad("BAD_ACTION", "Use the reschedule endpoint");
        return bookings.act(new BookingService.Actor(u.id(), u.role()), id, action, r == null ? null : r.reason());
    }

    @PostMapping("/biz/{bid}/bookings/{id}/reschedule")
    public Map<String, Object> reschedule(@PathVariable long bid, @PathVariable long id, @Valid @RequestBody RescheduleReq r) {
        access.owned(bid); bookings.viewForBusiness(id, bid);
        var u = CurrentUser.require();
        return bookings.reschedule(new BookingService.Actor(u.id(), u.role()), id, r.startAt());
    }

    // ============================================================ customers
    @GetMapping("/biz/{bid}/customers")
    public List<Map<String, Object>> customers(@PathVariable long bid, @RequestParam(required = false) String q) {
        access.owned(bid);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        var sql = """
            SELECT u.id, u.full_name, u.phone, count(*) AS bookings_count,
                   count(*) FILTER (WHERE bk.status='COMPLETED') AS completed_count,
                   count(*) FILTER (WHERE bk.status IN ('CANCELLED','REJECTED')) AS cancelled_count,
                   COALESCE(sum(bk.price_minor - bk.discount_minor) FILTER (WHERE bk.status='COMPLETED'),0) AS spent_minor,
                   max(bk.start_at) AS last_visit, n.note
            FROM bookings bk JOIN users u ON u.id=bk.customer_id
            LEFT JOIN business_customer_notes n ON n.business_id=bk.business_id AND n.customer_id=u.id
            WHERE bk.business_id=:b AND u.status='ACTIVE' %s GROUP BY u.id, n.note ORDER BY max(bk.start_at) DESC LIMIT 200""".formatted(like == null ? "" : "AND lower(u.full_name) LIKE :q ESCAPE '\\'");
        var spec = db.sql(sql).param("b", bid);
        if (like != null) spec = spec.param("q", like);
        return Rows.rows(spec.query().listOfRows());
    }

    public record NoteReq(@NotNull @Size(max = 1000) String note) {}
    @PutMapping("/biz/{bid}/customers/{cid}/note")
    public void note(@PathVariable long bid, @PathVariable long cid, @Valid @RequestBody NoteReq r) {
        access.owned(bid);
        if (db.sql("SELECT count(*) FROM bookings WHERE business_id=:b AND customer_id=:c").param("b", bid).param("c", cid).query(Long.class).single() == 0) throw ApiException.notFound("Customer");
        db.sql("INSERT INTO business_customer_notes(business_id, customer_id, note) VALUES (:b,:c,:n) ON CONFLICT (business_id, customer_id) DO UPDATE SET note=:n, updated_at=now()")
          .param("b", bid).param("c", cid).param("n", r.note().trim()).update();
    }

    // ============================================================ reviews / promotions / subscription
    @GetMapping("/biz/{bid}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable long bid) {
        access.owned(bid);
        return Rows.rows(db.sql("""
            SELECT r.id, r.rating, r.body, r.reply, r.replied_at, r.created_at, r.status, u.full_name AS author, bk.service_name, p.full_name AS professional_name
            FROM reviews r JOIN users u ON u.id=r.customer_id JOIN bookings bk ON bk.id=r.booking_id JOIN professionals p ON p.id=r.professional_id
            WHERE r.business_id=:b ORDER BY r.created_at DESC LIMIT 100""").param("b", bid).query().listOfRows());
    }

    public record PromoReq(@NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code, @Min(1) @Max(100) Integer percentOff, @Min(1) Long amountOffMinor,
                           Instant validTo, @Min(1) Integer maxRedemptions) {}

    @GetMapping("/biz/{bid}/promotions")
    public List<Map<String, Object>> promotions(@PathVariable long bid) {
        access.owned(bid);
        return Rows.rows(db.sql("SELECT id, code, percent_off, amount_off_minor, currency, valid_from, valid_to, max_redemptions, redemptions, active FROM promotions WHERE business_id=:b ORDER BY id DESC").param("b", bid).query().listOfRows());
    }

    @PostMapping("/biz/{bid}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPromo(@PathVariable long bid, @Valid @RequestBody PromoReq r) {
        var biz = access.owned(bid);
        if ((r.percentOff() == null) == (r.amountOffMinor() == null)) throw ApiException.bad("BAD_PROMO", "Provide exactly one of percentOff or amountOffMinor");
        try {
            long id = db.sql("INSERT INTO promotions(code, business_id, percent_off, amount_off_minor, currency, valid_to, max_redemptions) VALUES (:c,:b,:p,:a,:cur,:v,:m) RETURNING id")
                    .param("c", r.code().toUpperCase()).param("b", bid).param("p", r.percentOff()).param("a", r.amountOffMinor()).param("cur", r.amountOffMinor() == null ? null : biz.currency())
                    .param("v", r.validTo() == null ? null : Timestamp.from(r.validTo())).param("m", r.maxRedemptions()).query(Long.class).single();
            return Map.of("id", id);
        } catch (DuplicateKeyException e) { throw ApiException.conflict("PROMO_CODE_TAKEN", "Code already exists"); }
    }

    @PostMapping("/biz/{bid}/promotions/{id}/active")
    public void promoActive(@PathVariable long bid, @PathVariable long id, @RequestParam boolean value) {
        access.owned(bid);
        db.sql("UPDATE promotions SET active=:a WHERE id=:i AND business_id=:b").param("a", value).param("i", id).param("b", bid).update();
    }

    public record PlanReq(@NotBlank String planCode) {}
    /** Records the chosen plan only. Subscription BILLING IS NOT INTEGRATED: nothing is charged and plan prices are unset. */
    @PutMapping("/biz/{bid}/subscription")
    public Map<String, Object> plan(@PathVariable long bid, @Valid @RequestBody PlanReq r) {
        access.owned(bid);
        Long planId = db.sql("SELECT id FROM subscription_plans WHERE code=:c AND active").param("c", r.planCode()).query(Long.class).optional().orElseThrow(() -> ApiException.bad("BAD_PLAN", "Unknown plan"));
        db.sql("INSERT INTO business_subscriptions(business_id, plan_id) VALUES (:b,:p) ON CONFLICT (business_id) DO UPDATE SET plan_id=:p, status='ACTIVE'").param("b", bid).param("p", planId).update();
        audit.log(CurrentUser.require().id(), "SUBSCRIPTION_PLAN_CHANGED", "BUSINESS", bid, r.planCode());
        return Map.of("planCode", r.planCode(), "billing", "NOT_INTEGRATED");
    }

    // ============================================================ overview & analytics
    @GetMapping("/biz/{bid}/overview")
    public Map<String, Object> overview(@PathVariable long bid) {
        var biz = access.owned(bid);
        ZoneId z = biz.zone();
        LocalDate today = LocalDate.now(clock.withZone(z));
        Timestamp dayStart = Timestamp.from(today.atStartOfDay(z).toInstant()), dayEnd = Timestamp.from(today.plusDays(1).atStartOfDay(z).toInstant());
        Timestamp weekEnd = Timestamp.from(today.plusDays(8).atStartOfDay(z).toInstant());
        Timestamp monthStart = Timestamp.from(today.withDayOfMonth(1).atStartOfDay(z).toInstant());
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("currency", biz.currency());
        o.put("today", bookings.listForBusiness(bid, null, today, today, null, z).stream().filter(b -> List.of("PENDING", "CONFIRMED", "COMPLETED").contains(b.get("status"))).toList());
        o.put("upcomingCount", cnt("SELECT count(*) FROM bookings WHERE business_id=:b AND status IN ('PENDING','CONFIRMED') AND start_at >= :s AND start_at < :e", bid, dayEnd, weekEnd));
        o.put("pendingCount", cnt("SELECT count(*) FROM bookings WHERE business_id=:b AND status='PENDING' AND start_at >= :s AND start_at < :e", bid, dayStart, Timestamp.from(today.plusDays(365).atStartOfDay(z).toInstant())));
        o.put("completedThisMonth", cnt("SELECT count(*) FROM bookings WHERE business_id=:b AND status='COMPLETED' AND start_at >= :s AND start_at < :e", bid, monthStart, dayEnd));
        o.put("cancelledThisMonth", cnt("SELECT count(*) FROM bookings WHERE business_id=:b AND status IN ('CANCELLED','REJECTED') AND start_at >= :s AND start_at < :e", bid, monthStart, Timestamp.from(today.plusDays(32).atStartOfDay(z).toInstant())));
        // ACTUAL figure: only COMPLETED bookings count as revenue. Online-paid amounts are reported separately.
        o.put("revenueThisMonthMinor", db.sql("SELECT COALESCE(sum(price_minor - discount_minor),0) FROM bookings WHERE business_id=:b AND status='COMPLETED' AND start_at >= :s AND start_at < :e").param("b", bid).param("s", monthStart).param("e", dayEnd).query(Long.class).single());
        o.put("onlinePaidThisMonthMinor", db.sql("SELECT COALESCE(sum(p.amount_minor - p.refunded_minor),0) FROM payments p JOIN bookings bk ON bk.id=p.booking_id WHERE bk.business_id=:b AND p.status IN ('SUCCEEDED','PARTIALLY_REFUNDED') AND p.simulated=FALSE AND p.created_at >= :s").param("b", bid).param("s", monthStart).query(Long.class).single());
        o.put("onlinePaymentsSimulatedOnly", db.sql("SELECT count(*) FROM payments p JOIN bookings bk ON bk.id=p.booking_id WHERE bk.business_id=:b AND p.simulated").param("b", bid).query(Long.class).single() > 0);
        o.put("rating", Rows.row(db.sql("SELECT rating_avg, rating_count FROM businesses WHERE id=:b").param("b", bid).query().listOfRows().get(0)));
        return Rows.row(o);
    }

    private long cnt(String sql, long bid, Timestamp s, Timestamp e) { return db.sql(sql).param("b", bid).param("s", s).param("e", e).query(Long.class).single(); }

    @GetMapping("/biz/{bid}/analytics")
    public Map<String, Object> analytics(@PathVariable long bid, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        var biz = access.owned(bid);
        ZoneId z = biz.zone();
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(z));
        LocalDate start = from != null ? from : end.minusDays(29);
        if (end.isBefore(start) || start.plusDays(366).isBefore(end)) throw ApiException.bad("BAD_RANGE", "Range must be 1..366 days");
        Timestamp s = Timestamp.from(start.atStartOfDay(z).toInstant()), e = Timestamp.from(end.plusDays(1).atStartOfDay(z).toInstant());
        String tz = z.getId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start.toString()); out.put("to", end.toString()); out.put("currency", biz.currency()); out.put("timezone", tz);
        out.put("byDay", Rows.rows(db.sql("""
            SELECT (start_at AT TIME ZONE :tz)::date AS day, count(*) AS bookings,
                   count(*) FILTER (WHERE status='COMPLETED') AS completed,
                   COALESCE(sum(price_minor - discount_minor) FILTER (WHERE status='COMPLETED'),0) AS revenue_minor
            FROM bookings WHERE business_id=:b AND start_at >= :s AND start_at < :e AND status IN ('PENDING','CONFIRMED','COMPLETED','NO_SHOW')
            GROUP BY 1 ORDER BY 1""").param("tz", tz).param("b", bid).param("s", s).param("e", e).query().listOfRows()));
        var tot = db.sql("""
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE status='COMPLETED') AS completed,
                   count(*) FILTER (WHERE status IN ('CANCELLED','REJECTED')) AS cancelled,
                   COALESCE(sum(price_minor - discount_minor) FILTER (WHERE status='COMPLETED'),0) AS revenue_minor
            FROM bookings WHERE business_id=:b AND start_at >= :s AND start_at < :e""").param("b", bid).param("s", s).param("e", e).query().listOfRows().get(0);
        long total = ((Number) tot.get("total")).longValue(), completed = ((Number) tot.get("completed")).longValue(), cancelled = ((Number) tot.get("cancelled")).longValue();
        long revenue = ((Number) tot.get("revenue_minor")).longValue();
        out.put("totalBookings", total); out.put("completedBookings", completed); out.put("cancelledBookings", cancelled); out.put("revenueMinor", revenue);
        out.put("averageBookingValueMinor", completed == 0 ? 0 : revenue / completed);
        out.put("cancellationRate", total == 0 ? 0 : Math.round(cancelled * 1000.0 / total) / 10.0);
        var rep = db.sql("""
            SELECT count(*) FILTER (WHERE n >= 2) AS repeat_customers, count(*) AS customers FROM (
              SELECT customer_id, count(*) AS n FROM bookings WHERE business_id=:b AND status='COMPLETED' AND start_at >= :s AND start_at < :e GROUP BY customer_id) t""")
                .param("b", bid).param("s", s).param("e", e).query().listOfRows().get(0);
        long cust = ((Number) rep.get("customers")).longValue();
        out.put("repeatCustomerRate", cust == 0 ? 0 : Math.round(((Number) rep.get("repeat_customers")).longValue() * 1000.0 / cust) / 10.0);
        out.put("popularServices", Rows.rows(db.sql("""
            SELECT service_name AS name, count(*) AS bookings, COALESCE(sum(price_minor - discount_minor) FILTER (WHERE status='COMPLETED'),0) AS revenue_minor
            FROM bookings WHERE business_id=:b AND start_at >= :s AND start_at < :e AND status IN ('CONFIRMED','COMPLETED','PENDING')
            GROUP BY service_name ORDER BY count(*) DESC LIMIT 5""").param("b", bid).param("s", s).param("e", e).query().listOfRows()));
        // staff utilisation = booked minutes / scheduled minutes (working hours minus breaks) over the range
        List<Map<String, Object>> util = new ArrayList<>();
        for (var p : db.sql("SELECT id, full_name FROM professionals WHERE business_id=:b AND active ORDER BY full_name").param("b", bid).query().listOfRows()) {
            long pid = ((Number) p.get("id")).longValue();
            Map<Integer, Long> perWeekday = new HashMap<>();
            for (var h : db.sql("SELECT weekday, EXTRACT(EPOCH FROM (end_time - start_time))/60 - COALESCE(EXTRACT(EPOCH FROM (break_end - break_start))/60, 0) AS mins FROM professional_working_hours WHERE professional_id=:p").param("p", pid).query().listOfRows())
                perWeekday.put(((Number) h.get("weekday")).intValue(), ((Number) h.get("mins")).longValue());
            long scheduled = 0;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) scheduled += perWeekday.getOrDefault(d.getDayOfWeek().getValue(), 0L);
            long booked = db.sql("SELECT COALESCE(sum(EXTRACT(EPOCH FROM (end_at - start_at))/60),0)::bigint FROM bookings WHERE professional_id=:p AND status IN ('CONFIRMED','COMPLETED') AND start_at >= :s AND start_at < :e")
                    .param("p", pid).param("s", s).param("e", e).query(Long.class).single();
            util.add(Map.of("professionalId", pid, "name", p.get("full_name"), "bookedMinutes", booked, "scheduledMinutes", scheduled, "utilization", scheduled == 0 ? 0 : Math.round(booked * 1000.0 / scheduled) / 10.0));
        }
        out.put("staffUtilization", util);
        out.put("ratingTrend", Rows.rows(db.sql("""
            SELECT to_char(created_at AT TIME ZONE :tz, 'YYYY-MM') AS month, round(avg(rating)::numeric,2) AS average, count(*) AS reviews
            FROM reviews WHERE business_id=:b AND status='VISIBLE' AND created_at >= now() - interval '6 months' GROUP BY 1 ORDER BY 1""").param("tz", tz).param("b", bid).query().listOfRows()));
        return Rows.row(out);
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
