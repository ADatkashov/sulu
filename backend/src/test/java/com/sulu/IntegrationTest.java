package com.sulu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base class: real PostgreSQL (sulu_test), real Flyway migrations, real security. Each test builds its own tenant data via the API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcClient db;
    @Autowired protected PasswordEncoder encoder;
    protected static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");

    public record Res(int status, JsonNode body) {}

    protected Res call(MockHttpServletRequestBuilder b, String token, Object body, String... headers) throws Exception {
        if (token != null) b.header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andReturn();
        String s = r.getResponse().getContentAsString();
        return new Res(r.getResponse().getStatus(), s.isBlank() ? json.nullNode() : json.readTree(s));
    }
    protected Res get(String url, String token) throws Exception { return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token, null); }
    protected Res post(String url, String token, Object body, String... h) throws Exception { return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), token, body, h); }
    protected Res put(String url, String token, Object body) throws Exception { return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url), token, body); }

    protected String email() { return "t" + UUID.randomUUID().toString().substring(0, 8) + "@test.local"; }

    protected String register(String role) throws Exception {
        Res r = post("/api/auth/register", null, Map.of("email", email(), "password", "Passw0rd!x", "fullName", "Test " + role, "role", role));
        if (r.status() != 200) throw new IllegalStateException("register failed " + r.status() + " " + r.body());
        return r.body().get("accessToken").asText();
    }

    protected long userId(String token) throws Exception { return get("/api/me", token).body().get("id").asLong(); }

    protected String adminToken() throws Exception {
        String em = email();
        db.sql("INSERT INTO users(email, password_hash, full_name, role) VALUES (:e,:p,'Admin','PLATFORM_ADMIN')").param("e", em).param("p", encoder.encode("Passw0rd!x")).update();
        return post("/api/auth/login", null, Map.of("email", em, "password", "Passw0rd!x")).body().get("accessToken").asText();
    }

    /** A fully set up, APPROVED business with 1 service (60 min, 10000 KZT) and 1 professional working every day 09:00–21:00. */
    public record Fixture(String ownerToken, long businessId, long serviceId, long professionalId, long categoryId) {}

    protected Fixture fixture(boolean autoConfirm, int cancellationHours, int bufferMinutes) throws Exception {
        String owner = register("BUSINESS_OWNER");
        long bid = post("/api/business", owner, Map.of("name", "Test Salon", "city", "Алматы", "address", "Test st. 1")).body().get("id").asLong();
        put("/api/biz/" + bid, owner, Map.ofEntries(Map.entry("name", "Test Salon"), Map.entry("address", "Test st. 1"), Map.entry("autoConfirm", autoConfirm),
                Map.entry("minLeadMinutes", 60), Map.entry("maxAdvanceDays", 60), Map.entry("bufferMinutes", bufferMinutes), Map.entry("cancellationHours", cancellationHours)));
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int d = 1; d <= 7; d++) hours.add(Map.of("weekday", d, "openTime", "09:00", "closeTime", "21:00"));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/biz/" + bid + "/hours"), owner, hours);
        long cat = db.sql("SELECT id FROM service_categories WHERE slug='haircut'").query(Long.class).single();
        long sid = post("/api/biz/" + bid + "/services", owner, Map.of("categoryId", cat, "name", "Haircut", "priceMinor", 1000000, "durationMin", 60)).body().get("id").asLong();
        long pid = post("/api/biz/" + bid + "/professionals", owner, Map.of("fullName", "Pro One", "experienceYears", 3, "serviceIds", List.of(sid))).body().get("id").asLong();
        List<Map<String, Object>> ph = new ArrayList<>();
        for (int d = 1; d <= 7; d++) ph.add(Map.of("weekday", d, "startTime", "09:00", "endTime", "21:00", "breakStart", "13:00", "breakEnd", "14:00"));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/biz/" + bid + "/professionals/" + pid + "/hours"), owner, ph);
        db.sql("UPDATE businesses SET status='APPROVED' WHERE id=:i").param("i", bid).update();
        return new Fixture(owner, bid, sid, pid, cat);
    }

    /** Start of a slot on a future local date (days ahead) at the given local time. */
    protected Instant at(int daysAhead, int hour, int minute) {
        return LocalDate.now(ALMATY).plusDays(daysAhead).atTime(hour, minute).atZone(ALMATY).toInstant();
    }

    protected Res book(String token, Fixture f, Instant start, String... headers) throws Exception {
        return post("/api/bookings", token, Map.of("serviceId", f.serviceId(), "professionalId", f.professionalId(), "startAt", start.toString()), headers);
    }

    /** Inserts a booking directly (for past appointments which the API rightly refuses to create). */
    protected long insertBooking(long customerId, Fixture f, Instant start, String status) {
        return db.sql("""
            INSERT INTO bookings(reference, customer_id, business_id, professional_id, service_id, service_name, start_at, end_at, status, price_minor, currency)
            VALUES (:r,:c,:b,:p,:s,'Haircut',:st,:en,:status,1000000,'KZT') RETURNING id""").param("r", "T-" + UUID.randomUUID().toString().substring(0, 10)).param("c", customerId)
            .param("b", f.businessId()).param("p", f.professionalId()).param("s", f.serviceId()).param("st", java.sql.Timestamp.from(start)).param("en", java.sql.Timestamp.from(start.plusSeconds(3600)))
            .param("status", status).query(Long.class).single();
    }
}
