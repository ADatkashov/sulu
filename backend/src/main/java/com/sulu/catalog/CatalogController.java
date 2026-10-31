package com.sulu.catalog;

import com.sulu.common.ApiException;
import com.sulu.common.PageResult;
import com.sulu.common.Rows;
import com.sulu.common.SuluProperties;
import com.sulu.security.CurrentUser;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

/** Public marketplace discovery: categories, salons, professionals, services, unified search, reviews. */
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final JdbcClient db;
    private final SuluProperties props;
    public CatalogController(JdbcClient db, SuluProperties props) { this.db = db; this.props = props; }

    private static String like(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + s + "%";
    }
    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static int clampSize(int size) { return Math.max(1, Math.min(size, 50)); }

    /** Tiny helper to build parameterised dynamic SQL without string-concatenating user input. */
    private static final class Q {
        final StringBuilder where = new StringBuilder(" WHERE 1=1");
        final Map<String, Object> p = new LinkedHashMap<>();
        Q and(String clause, String name, Object value) { where.append(" AND ").append(clause); p.put(name, value); return this; }
        Q and(String clause) { where.append(" AND ").append(clause); return this; }
    }
    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec s, Map<String, Object> p) { return s.params(p); }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, String> settings = new HashMap<>();
        db.sql("SELECT key, value FROM platform_settings").query().listOfRows().forEach(r -> settings.put((String) r.get("key"), (String) r.get("value")));
        List<String> cities = db.sql("SELECT DISTINCT city FROM businesses WHERE status='APPROVED' ORDER BY city").query(String.class).list();
        return Map.of("defaultCity", settings.getOrDefault("default_city", "Алматы"),
                "defaultCurrency", settings.getOrDefault("default_currency", "KZT"),
                "cities", cities, "locales", List.of("kk", "ru", "en"),
                "simulatedPayments", props.payments().simulatedEnabled());
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return Rows.rows(db.sql("SELECT id, slug, name_kk, name_ru, name_en, name_tr, icon FROM service_categories WHERE active ORDER BY sort_order").query().listOfRows());
    }

    // ---------------- businesses ----------------
    @GetMapping("/businesses")
    public PageResult<Map<String, Object>> businesses(
            @RequestParam(required = false) String q, @RequestParam(required = false) String city,
            @RequestParam(required = false) String category, @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Long minPriceMinor, @RequestParam(required = false) Long maxPriceMinor,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        size = clampSize(size); page = Math.max(0, page);
        Q f = new Q().and("b.status='APPROVED'");
        if (has(city)) f.and("b.city = :city", "city", city);
        if (has(q)) f.and("""
            (lower(b.name) LIKE :q ESCAPE '\\' OR EXISTS (SELECT 1 FROM services s WHERE s.business_id=b.id AND s.active AND (lower(s.name) LIKE :q ESCAPE '\\' OR lower(s.tags) LIKE :q ESCAPE '\\'))
              OR EXISTS (SELECT 1 FROM professionals p WHERE p.business_id=b.id AND p.active AND (lower(p.full_name) LIKE :q ESCAPE '\\' OR lower(p.specialties) LIKE :q ESCAPE '\\')))""", "q", like(q));
        if (has(category)) f.and("EXISTS (SELECT 1 FROM services s JOIN service_categories c ON c.id=s.category_id WHERE s.business_id=b.id AND s.active AND c.slug=:cat)", "cat", category);
        if (minRating != null) f.and("b.rating_avg >= :minRating", "minRating", minRating);
        if (minPriceMinor != null) f.and("EXISTS (SELECT 1 FROM services s WHERE s.business_id=b.id AND s.active AND s.price_minor >= :minP)", "minP", minPriceMinor);
        if (maxPriceMinor != null) f.and("EXISTS (SELECT 1 FROM services s WHERE s.business_id=b.id AND s.active AND s.price_minor <= :maxP)", "maxP", maxPriceMinor);
        boolean geo = lat != null && lng != null;
        String dist = geo ? "CASE WHEN b.lat IS NULL THEN NULL ELSE 6371 * acos(LEAST(1, cos(radians(:lat))*cos(radians(b.lat))*cos(radians(b.lng)-radians(:lng)) + sin(radians(:lat))*sin(radians(b.lat)))) END" : "NULL::double precision";
        if (geo) { f.p.put("lat", lat); f.p.put("lng", lng); }
        String order = switch (sort) {
            case "price_asc" -> "min_price ASC NULLS LAST, b.rating_avg DESC";
            case "price_desc" -> "min_price DESC NULLS LAST, b.rating_avg DESC";
            case "distance" -> geo ? "distance_km ASC NULLS LAST, b.rating_avg DESC" : "b.rating_avg DESC, b.rating_count DESC";
            case "name" -> "lower(b.name) ASC";
            default -> "b.rating_avg DESC, b.rating_count DESC, b.id";
        };
        CurrentUser u = CurrentUser.optional();
        String fav = u == null ? "FALSE" : "EXISTS (SELECT 1 FROM favorites fv WHERE fv.user_id=:uid AND fv.kind='BUSINESS' AND fv.target_id=b.id)";
        if (u != null) f.p.put("uid", u.id());
        long total = bind(db.sql("SELECT count(*) FROM businesses b" + f.where), f.p).query(Long.class).single();
        Map<String, Object> pp = new LinkedHashMap<>(f.p); pp.put("lim", size); pp.put("off", page * size);
        var rows = bind(db.sql("SELECT b.id, b.name, b.kind, b.city, b.address, b.lat, b.lng, b.rating_avg, b.rating_count, b.image_key, b.is_demo, b.currency, " +
                "(SELECT min(s.price_minor) FROM services s WHERE s.business_id=b.id AND s.active) AS min_price, " + dist + " AS distance_km, " + fav + " AS favorite " +
                "FROM businesses b" + f.where + " ORDER BY " + order + " LIMIT :lim OFFSET :off"), pp).query().listOfRows();
        return new PageResult<>(Rows.rows(rows), page, size, total);
    }

    @GetMapping("/businesses/{id}")
    public Map<String, Object> business(@PathVariable long id) {
        CurrentUser u = CurrentUser.optional();
        var b = db.sql("""
            SELECT b.id, b.name, b.kind, b.description, b.city, b.address, b.lat, b.lng, b.phone, b.timezone, b.currency, b.image_key, b.is_demo,
                   b.rating_avg, b.rating_count, b.cancellation_hours, b.auto_confirm, b.min_lead_minutes, b.max_advance_days
            FROM businesses b WHERE b.id=:id AND b.status='APPROVED'""")
            .param("id", id).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Business"));
        Map<String, Object> out = Rows.row(b);
        out.put("favorite", u != null && db.sql("SELECT count(*) FROM favorites WHERE user_id=:u AND kind='BUSINESS' AND target_id=:i").param("u", u.id()).param("i", id).query(Long.class).single() > 0);
        out.put("hours", Rows.rows(db.sql("SELECT weekday, open_time, close_time FROM business_hours WHERE business_id=:i ORDER BY weekday").param("i", id).query().listOfRows()));
        out.put("services", services(null, id, null, null, null, null, null, null, "price_asc", 0, 50).items());
        out.put("professionals", professionals(null, id, null, null, null, 0, 50).items());
        return out;
    }

    // ---------------- services ----------------
    private static final String SERVICE_SELECT = """
        SELECT s.id, s.name, s.description, s.price_minor, s.price_from, s.currency, s.duration_min, s.at_home, s.tags, s.image_key,
               s.business_id, b.name AS business_name, b.city, b.address, b.rating_avg AS business_rating, b.rating_count AS business_rating_count,
               b.is_demo, c.slug AS category_slug, c.name_kk, c.name_ru, c.name_en, c.name_tr
        FROM services s JOIN businesses b ON b.id=s.business_id JOIN service_categories c ON c.id=s.category_id""";

    @GetMapping("/services")
    public PageResult<Map<String, Object>> services(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String category, @RequestParam(required = false) String city,
            @RequestParam(required = false) Long minPriceMinor, @RequestParam(required = false) Long maxPriceMinor,
            @RequestParam(required = false) Boolean atHome, @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        size = clampSize(size); page = Math.max(0, page);
        Q f = new Q().and("s.active").and("b.status='APPROVED'");
        if (businessId != null) f.and("s.business_id=:bid", "bid", businessId);
        if (has(city)) f.and("b.city=:city", "city", city);
        if (has(category)) f.and("c.slug=:cat", "cat", category);
        if (has(q)) f.and("(lower(s.name) LIKE :q ESCAPE '\\' OR lower(s.tags) LIKE :q ESCAPE '\\' OR lower(b.name) LIKE :q ESCAPE '\\' OR lower(c.name_ru) LIKE :q ESCAPE '\\' OR lower(c.name_kk) LIKE :q ESCAPE '\\' OR lower(c.name_en) LIKE :q ESCAPE '\\')", "q", like(q));
        if (minPriceMinor != null) f.and("s.price_minor >= :minP", "minP", minPriceMinor);
        if (maxPriceMinor != null) f.and("s.price_minor <= :maxP", "maxP", maxPriceMinor);
        if (atHome != null && atHome) f.and("s.at_home");
        if (has(gender) && (gender.equals("FEMALE") || gender.equals("MALE")))
            f.and("EXISTS (SELECT 1 FROM professional_services ps JOIN professionals p ON p.id=ps.professional_id WHERE ps.service_id=s.id AND p.active AND p.gender=:gender)", "gender", gender);
        String order = switch (sort) {
            case "price_asc" -> "s.price_minor ASC, s.id";
            case "price_desc" -> "s.price_minor DESC, s.id";
            case "duration" -> "s.duration_min ASC, s.id";
            default -> "b.rating_avg DESC, b.rating_count DESC, s.id";
        };
        long total = bind(db.sql("SELECT count(*) FROM services s JOIN businesses b ON b.id=s.business_id JOIN service_categories c ON c.id=s.category_id" + f.where), f.p).query(Long.class).single();
        Map<String, Object> pp = new LinkedHashMap<>(f.p); pp.put("lim", size); pp.put("off", page * size);
        var rows = bind(db.sql(SERVICE_SELECT + f.where + " ORDER BY " + order + " LIMIT :lim OFFSET :off"), pp).query().listOfRows();
        return new PageResult<>(Rows.rows(rows), page, size, total);
    }

    @GetMapping("/services/{id}")
    public Map<String, Object> service(@PathVariable long id) {
        CurrentUser u = CurrentUser.optional();
        var s = db.sql(SERVICE_SELECT + " WHERE s.id=:id AND s.active AND b.status='APPROVED'").param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Service"));
        Map<String, Object> out = Rows.row(s);
        var biz = db.sql("SELECT cancellation_hours, timezone, auto_confirm, phone, lat, lng FROM businesses WHERE id=:i").param("i", s.get("business_id")).query().listOfRows().get(0);
        out.putAll(Rows.row(biz));
        out.put("professionals", Rows.rows(db.sql("""
            SELECT p.id, p.full_name, p.title, p.avatar_key, p.rating_avg, p.rating_count, p.experience_years, p.gender
            FROM professional_services ps JOIN professionals p ON p.id=ps.professional_id
            WHERE ps.service_id=:i AND p.active ORDER BY p.rating_avg DESC, p.id""").param("i", id).query().listOfRows()));
        out.put("favorite", u != null && db.sql("SELECT count(*) FROM favorites WHERE user_id=:u AND kind='SERVICE' AND target_id=:i").param("u", u.id()).param("i", id).query(Long.class).single() > 0);
        return out;
    }

    // ---------------- professionals ----------------
    @GetMapping("/professionals")
    public PageResult<Map<String, Object>> professionals(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String category, @RequestParam(required = false) String city,
            @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        size = clampSize(size); page = Math.max(0, page);
        Q f = new Q().and("p.active").and("b.status='APPROVED'");
        if (businessId != null) f.and("p.business_id=:bid", "bid", businessId);
        if (has(city)) f.and("b.city=:city", "city", city);
        if (has(gender) && (gender.equals("FEMALE") || gender.equals("MALE"))) f.and("p.gender=:gender", "gender", gender);
        if (has(q)) f.and("(lower(p.full_name) LIKE :q ESCAPE '\\' OR lower(p.specialties) LIKE :q ESCAPE '\\' OR lower(p.title) LIKE :q ESCAPE '\\')", "q", like(q));
        if (has(category)) f.and("EXISTS (SELECT 1 FROM professional_services ps JOIN services s ON s.id=ps.service_id JOIN service_categories c ON c.id=s.category_id WHERE ps.professional_id=p.id AND s.active AND c.slug=:cat)", "cat", category);
        CurrentUser u = CurrentUser.optional();
        String fav = u == null ? "FALSE" : "EXISTS (SELECT 1 FROM favorites fv WHERE fv.user_id=:uid AND fv.kind='PROFESSIONAL' AND fv.target_id=p.id)";
        if (u != null) f.p.put("uid", u.id());
        long total = bind(db.sql("SELECT count(*) FROM professionals p JOIN businesses b ON b.id=p.business_id" + f.where), f.p).query(Long.class).single();
        Map<String, Object> pp = new LinkedHashMap<>(f.p); pp.put("lim", size); pp.put("off", page * size);
        var rows = bind(db.sql("SELECT p.id, p.full_name, p.title, p.specialties, p.avatar_key, p.experience_years, p.gender, p.rating_avg, p.rating_count, p.business_id, " +
                "b.name AS business_name, b.city, b.is_demo, " + fav + " AS favorite FROM professionals p JOIN businesses b ON b.id=p.business_id" + f.where +
                " ORDER BY p.rating_avg DESC, p.rating_count DESC, p.id LIMIT :lim OFFSET :off"), pp).query().listOfRows();
        return new PageResult<>(Rows.rows(rows), page, size, total);
    }

    @GetMapping("/professionals/{id}")
    public Map<String, Object> professional(@PathVariable long id) {
        CurrentUser u = CurrentUser.optional();
        var p = db.sql("""
            SELECT p.id, p.full_name, p.title, p.bio, p.specialties, p.avatar_key, p.experience_years, p.gender, p.rating_avg, p.rating_count,
                   p.business_id, b.name AS business_name, b.city, b.address, b.lat, b.lng, b.timezone, b.currency, b.is_demo, b.cancellation_hours
            FROM professionals p JOIN businesses b ON b.id=p.business_id WHERE p.id=:id AND p.active AND b.status='APPROVED'""")
            .param("id", id).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Professional"));
        Map<String, Object> out = Rows.row(p);
        out.put("services", Rows.rows(db.sql("""
            SELECT s.id, s.name, s.price_minor, s.price_from, s.currency, s.duration_min, s.image_key
            FROM professional_services ps JOIN services s ON s.id=ps.service_id WHERE ps.professional_id=:i AND s.active ORDER BY s.name""").param("i", id).query().listOfRows()));
        out.put("portfolio", Rows.rows(db.sql("SELECT id, image_key, caption FROM portfolio_images WHERE professional_id=:i ORDER BY id DESC").param("i", id).query().listOfRows()));
        out.put("workingHours", Rows.rows(db.sql("SELECT weekday, start_time, end_time, break_start, break_end FROM professional_working_hours WHERE professional_id=:i ORDER BY weekday").param("i", id).query().listOfRows()));
        out.put("favorite", u != null && db.sql("SELECT count(*) FROM favorites WHERE user_id=:u AND kind='PROFESSIONAL' AND target_id=:i").param("u", u.id()).param("i", id).query(Long.class).single() > 0);
        return out;
    }

    // ---------------- unified search ----------------
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q, @RequestParam(required = false) String city) {
        if (!has(q) || q.trim().length() < 2) return Map.of("businesses", List.of(), "professionals", List.of(), "services", List.of(), "categories", List.of());
        String lk = like(q);
        var cats = Rows.rows(db.sql("SELECT id, slug, name_kk, name_ru, name_en, name_tr, icon FROM service_categories WHERE active AND (lower(name_kk) LIKE :q ESCAPE '\\' OR lower(name_ru) LIKE :q ESCAPE '\\' OR lower(name_en) LIKE :q ESCAPE '\\' OR lower(slug) LIKE :q ESCAPE '\\') LIMIT 5").param("q", lk).query().listOfRows());
        return Map.of("categories", cats,
                "businesses", businesses(q, city, null, null, null, null, null, null, "rating", 0, 5).items(),
                "professionals", professionals(q, null, null, city, null, 0, 5).items(),
                "services", services(q, null, null, city, null, null, null, null, "rating", 0, 5).items());
    }

    // ---------------- reviews ----------------
    @GetMapping("/reviews")
    public Map<String, Object> reviews(@RequestParam(required = false) Long businessId, @RequestParam(required = false) Long professionalId,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        if (businessId == null && professionalId == null) throw ApiException.bad("MISSING_FILTER", "businessId or professionalId is required");
        size = clampSize(size); page = Math.max(0, page);
        String col = professionalId != null ? "r.professional_id" : "r.business_id";
        long target = professionalId != null ? professionalId : businessId;
        var rows = db.sql("""
            SELECT r.id, r.rating, r.body, r.reply, r.replied_at, r.created_at, u.full_name AS author, bk.service_name
            FROM reviews r JOIN users u ON u.id=r.customer_id JOIN bookings bk ON bk.id=r.booking_id
            WHERE r.status='VISIBLE' AND %s=:t ORDER BY r.created_at DESC, r.id DESC LIMIT :l OFFSET :o""".formatted(col))
            .param("t", target).param("l", size).param("o", page * size).query().listOfRows();
        List<Map<String, Object>> items = new ArrayList<>();
        for (var r : Rows.rows(rows)) { r.put("author", shortName((String) r.get("author"))); items.add(r); }
        var dist = db.sql("SELECT rating, count(*) AS n FROM reviews r WHERE r.status='VISIBLE' AND " + col + "=:t GROUP BY rating").param("t", target).query().listOfRows();
        Map<Integer, Long> d = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 5; i >= 1; i--) d.put(i, 0L);
        long count = 0, sum = 0;
        for (var r : dist) { int rt = ((Number) r.get("rating")).intValue(); long n = ((Number) r.get("n")).longValue(); d.put(rt, n); count += n; sum += rt * n; }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items); out.put("count", count);
        out.put("average", count == 0 ? 0 : Math.round(sum * 100.0 / count) / 100.0);
        out.put("distribution", d);
        return out;
    }

    /** Privacy: only first name and last initial are public. */
    static String shortName(String full) {
        if (full == null || full.isBlank()) return "—";
        String[] p = full.trim().split("\\s+");
        return p.length == 1 ? p[0] : p[0] + " " + p[p.length - 1].charAt(0) + ".";
    }
}
