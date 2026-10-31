package com.sulu.booking;

import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import com.sulu.engagement.AuditService;
import com.sulu.engagement.NotificationService;
import com.sulu.payment.PaymentService;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    public record CreateRequest(Long serviceId, Long professionalId, Instant startAt, String note, String promoCode) {}
    public record Actor(long userId, String role) {}

    /** Valid status transitions. Anything else is rejected; clients can never set a status directly. */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("CONFIRMED", "REJECTED", "CANCELLED"),
            "CONFIRMED", Set.of("COMPLETED", "CANCELLED", "NO_SHOW"));
    static final Map<String, String> ACTIONS = Map.of("confirm", "CONFIRMED", "reject", "REJECTED", "complete", "COMPLETED",
            "no-show", "NO_SHOW", "cancel", "CANCELLED");

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final JdbcClient db;
    private final AvailabilityService availability;
    private final NotificationService notifications;
    private final PaymentService payments;
    private final AuditService audit;
    private final Clock clock;

    public BookingService(JdbcClient db, AvailabilityService availability, NotificationService notifications,
                          PaymentService payments, AuditService audit, Clock clock) {
        this.db = db; this.availability = availability; this.notifications = notifications; this.payments = payments; this.audit = audit; this.clock = clock;
    }

    // ------------------------------------------------------------------ create
    @Transactional
    public Map<String, Object> create(long customerId, CreateRequest r, String idemKey) {
        if (idemKey != null && !idemKey.isBlank()) {
            var existing = db.sql("SELECT id FROM bookings WHERE customer_id=:c AND idempotency_key=:k").param("c", customerId).param("k", idemKey).query(Long.class).optional();
            if (existing.isPresent()) return viewForCustomer(existing.get(), customerId);
        } else idemKey = null;

        var svc = db.sql("""
            SELECT s.id, s.name, s.price_minor, s.currency, s.duration_min, s.business_id, b.auto_confirm, b.owner_id, b.timezone, b.name AS business_name
            FROM services s JOIN businesses b ON b.id=s.business_id WHERE s.id=:s AND s.active AND b.status='APPROVED'""")
            .param("s", r.serviceId()).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Service"));
        ZoneId zone = ZoneId.of((String) svc.get("timezone"));
        LocalDate date = LocalDate.ofInstant(r.startAt(), zone);

        List<Long> candidates = r.professionalId() != null ? List.of(r.professionalId()) : availability.eligibleProfessionals(r.serviceId());
        Long chosen = null;
        for (long pid : candidates) {
            db.sql("SELECT pg_advisory_xact_lock(:k)").param("k", pid).query().singleRow(); // serialise concurrent bookings per professional
            AvailabilityService.Context c;
            try { c = availability.context(r.serviceId(), pid); } catch (ApiException e) { if (r.professionalId() != null) throw e; continue; }
            boolean ok = availability.slots(c, pid, date, null).stream().anyMatch(s -> s.start().equals(r.startAt()));
            if (ok) { chosen = pid; break; }
        }
        if (chosen == null) throw slotUnavailable(r.serviceId(), r.professionalId(), date);

        long price = ((Number) svc.get("price_minor")).longValue();
        String currency = ((String) svc.get("currency")).trim();
        long discount = 0; Long promoId = null;
        if (r.promoCode() != null && !r.promoCode().isBlank()) {
            var promo = lockPromo(r.promoCode(), ((Number) svc.get("business_id")).longValue(), currency);
            promoId = ((Number) promo.get("id")).longValue();
            discount = promo.get("percent_off") != null ? price * ((Number) promo.get("percent_off")).intValue() / 100
                    : Math.min(price, ((Number) promo.get("amount_off_minor")).longValue());
        }
        int commissionBps = Integer.parseInt(db.sql("SELECT value FROM platform_settings WHERE key='commission_bps'").query(String.class).optional().orElse("0"));
        boolean auto = (Boolean) svc.get("auto_confirm");
        Instant end = r.startAt().plus(Duration.ofMinutes(((Number) svc.get("duration_min")).longValue()));
        long id;
        try {
            id = db.sql("""
                INSERT INTO bookings(reference, customer_id, business_id, professional_id, service_id, service_name, start_at, end_at, status,
                                     price_minor, discount_minor, currency, promotion_id, commission_bps, customer_note, idempotency_key)
                VALUES (:ref,:c,:b,:p,:s,:sn,:st,:en,:status,:price,:disc,:cur,:promo,:bps,:note,:idem) RETURNING id""")
                .param("ref", newReference()).param("c", customerId).param("b", svc.get("business_id")).param("p", chosen).param("s", r.serviceId())
                .param("sn", svc.get("name")).param("st", Timestamp.from(r.startAt())).param("en", Timestamp.from(end))
                .param("status", auto ? "CONFIRMED" : "PENDING").param("price", price).param("disc", discount).param("cur", currency)
                .param("promo", promoId).param("bps", commissionBps).param("note", trim(r.note(), 500)).param("idem", idemKey)
                .query(Long.class).single();
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "This time was just taken. Please choose another time.");
            throw e;
        }
        if (promoId != null) {
            try {
                db.sql("INSERT INTO promotion_redemptions(promotion_id, user_id, booking_id) VALUES (:p,:u,:b)").param("p", promoId).param("u", customerId).param("b", id).update();
            } catch (DuplicateKeyException e) { throw ApiException.conflict("PROMO_ALREADY_USED", "You have already used this promotion code"); }
            db.sql("UPDATE promotions SET redemptions=redemptions+1 WHERE id=:i").param("i", promoId).update();
        }
        history(id, null, auto ? "CONFIRMED" : "PENDING", customerId, "created");

        String link = "/bookings/" + id;
        notifications.notify(customerId, auto ? "BOOKING_CONFIRMED" : "BOOKING_CREATED", auto ? "Запись подтверждена" : "Запись создана",
                svc.get("business_name") + " · " + svc.get("name"), link, "booking:" + id + ":created");
        notifyBusinessSide(id, ((Number) svc.get("owner_id")).longValue(), chosen, "BOOKING_NEW", "Новая запись", (String) svc.get("name"));
        return viewForCustomer(id, customerId);
    }

    private ApiException slotUnavailable(long serviceId, Long professionalId, LocalDate date) {
        List<AvailabilityService.Slot> alts = professionalId == null ? availability.slotsAny(serviceId, date) : availability.slots(serviceId, professionalId, date, null);
        return new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "This time is no longer available",
                Map.of("alternatives", alts.stream().limit(12).map(s -> Map.of("start", s.start(), "professionalId", s.professionalId())).toList()));
    }

    private Map<String, Object> lockPromo(String code, long businessId, String currency) {
        var p = db.sql("SELECT * FROM promotions WHERE upper(code)=upper(:c) FOR UPDATE").param("c", code.trim()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.unprocessable("PROMO_INVALID", "Promotion code is not valid"));
        Instant now = clock.instant();
        boolean ok = (Boolean) p.get("active")
                && !((Timestamp) p.get("valid_from")).toInstant().isAfter(now)
                && (p.get("valid_to") == null || ((Timestamp) p.get("valid_to")).toInstant().isAfter(now))
                && (p.get("business_id") == null || ((Number) p.get("business_id")).longValue() == businessId)
                && (p.get("amount_off_minor") == null || (p.get("currency") != null && p.get("currency").toString().trim().equals(currency)))
                && (p.get("max_redemptions") == null || ((Number) p.get("redemptions")).intValue() < ((Number) p.get("max_redemptions")).intValue());
        if (!ok) throw ApiException.unprocessable("PROMO_INVALID", "Promotion code is not valid");
        return p;
    }

    // ------------------------------------------------------------------ state changes
    @Transactional
    public Map<String, Object> act(Actor actor, long bookingId, String action, String reason) {
        String to = ACTIONS.get(action);
        if (to == null) throw ApiException.bad("BAD_ACTION", "Unknown action");
        var bk = lock(bookingId, actor);
        String from = (String) bk.get("status");
        boolean customer = "CUSTOMER".equals(actor.role());
        if (customer && !"CANCELLED".equals(to)) throw ApiException.forbidden("Customers can only cancel");
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
            throw ApiException.conflict("INVALID_TRANSITION", "Cannot change booking from " + from + " to " + to);
        Instant now = clock.instant();
        Instant start = ((Timestamp) bk.get("start_at")).toInstant();
        if (("COMPLETED".equals(to) || "NO_SHOW".equals(to)) && now.isBefore(start))
            throw ApiException.unprocessable("TOO_EARLY", "Appointment has not started yet");
        if (customer && !withinFreeWindow(bk, now)) throw ApiException.unprocessable("CANCELLATION_WINDOW_PASSED", "Free cancellation period has ended; please contact the business");

        db.sql("UPDATE bookings SET status=:to, cancel_reason=COALESCE(:r, cancel_reason), updated_at=now() WHERE id=:i AND status=:from")
          .param("to", to).param("r", trim(reason, 200)).param("i", bookingId).param("from", from).update();
        history(bookingId, from, to, actor.userId(), trim(reason, 200));

        if ("REJECTED".equals(to) || ("CANCELLED".equals(to) && !customer)) releasePromo(bookingId);
        if ("CANCELLED".equals(to) || "REJECTED".equals(to)) {
            boolean refund = !customer || withinFreeWindow(bk, now);
            if (refund) payments.refundForBooking(bookingId, actor.userId());
        }
        if ("COMPLETED".equals(to)) audit.log(actor.userId(), "BOOKING_COMPLETED", "BOOKING", bookingId, null);
        if ("ADMIN".equals(actorKind(actor))) audit.log(actor.userId(), "ADMIN_BOOKING_" + to, "BOOKING", bookingId, reason);

        long customerId = ((Number) bk.get("customer_id")).longValue();
        String sn = (String) bk.get("service_name");
        if (customer) notifyBusinessSide(bookingId, ((Number) bk.get("owner_id")).longValue(), ((Number) bk.get("professional_id")).longValue(), "BOOKING_CANCELLED", "Клиент отменил запись", sn);
        else notifications.notify(customerId, "BOOKING_" + to, titleFor(to), (String) bk.get("business_name") + " · " + sn, "/bookings/" + bookingId, "booking:" + bookingId + ":" + to);
        return Map.of("id", bookingId, "status", to);
    }

    @Transactional
    public Map<String, Object> reschedule(Actor actor, long bookingId, Instant newStart) {
        var bk = lock(bookingId, actor);
        String from = (String) bk.get("status");
        if (!Set.of("PENDING", "CONFIRMED").contains(from)) throw ApiException.conflict("INVALID_TRANSITION", "Only active bookings can be rescheduled");
        Instant now = clock.instant();
        boolean customer = "CUSTOMER".equals(actor.role());
        if (customer && !withinFreeWindow(bk, now)) throw ApiException.unprocessable("RESCHEDULE_WINDOW_PASSED", "Rescheduling period has ended; please contact the business");
        long pid = ((Number) bk.get("professional_id")).longValue(), sid = ((Number) bk.get("service_id")).longValue();
        db.sql("SELECT pg_advisory_xact_lock(:k)").param("k", pid).query().singleRow();
        AvailabilityService.Context c = availability.context(sid, pid);
        LocalDate date = LocalDate.ofInstant(newStart, c.zone());
        if (availability.slots(c, pid, date, bookingId).stream().noneMatch(s -> s.start().equals(newStart))) throw slotUnavailable(sid, pid, date);
        Instant end = newStart.plus(Duration.ofMinutes(c.durationMin()));
        try {
            db.sql("UPDATE bookings SET start_at=:s, end_at=:e, status=:st, updated_at=now() WHERE id=:i")
              .param("s", Timestamp.from(newStart)).param("e", Timestamp.from(end))
              .param("st", customer && !(Boolean) bk.get("auto_confirm") ? "PENDING" : from).param("i", bookingId).update();
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "This time was just taken. Please choose another time.");
            throw e;
        }
        history(bookingId, from, customer && !(Boolean) bk.get("auto_confirm") ? "PENDING" : from, actor.userId(), "rescheduled to " + newStart);
        long customerId = ((Number) bk.get("customer_id")).longValue();
        String sn = (String) bk.get("service_name");
        if (customer) notifyBusinessSide(bookingId, ((Number) bk.get("owner_id")).longValue(), pid, "BOOKING_RESCHEDULED", "Клиент перенёс запись", sn);
        else notifications.notify(customerId, "BOOKING_RESCHEDULED", "Запись перенесена", (String) bk.get("business_name") + " · " + sn, "/bookings/" + bookingId, "booking:" + bookingId + ":resched:" + newStart.getEpochSecond());
        return Map.of("id", bookingId, "startAt", newStart, "endAt", end);
    }

    // ------------------------------------------------------------------ reads
    public Map<String, Object> viewForCustomer(long id, long customerId) {
        var rows = db.sql(SELECT + " WHERE bk.id=:i AND bk.customer_id=:c").param("i", id).param("c", customerId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Booking");
        return decorate(rows.get(0), false);
    }

    public Map<String, Object> viewForBusiness(long id, long businessId) {
        var rows = db.sql(SELECT + " WHERE bk.id=:i AND bk.business_id=:b").param("i", id).param("b", businessId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Booking");
        return decorate(rows.get(0), true);
    }

    public List<Map<String, Object>> listForCustomer(long customerId, String scope, int page, int size) {
        String cond = switch (scope == null ? "upcoming" : scope) {
            case "past" -> "(bk.status IN ('COMPLETED','NO_SHOW') OR (bk.status IN ('PENDING','CONFIRMED') AND bk.end_at < now()))";
            case "cancelled" -> "bk.status IN ('CANCELLED','REJECTED')";
            default -> "(bk.status IN ('PENDING','CONFIRMED') AND bk.end_at >= now())";
        };
        String order = "upcoming".equals(scope == null ? "upcoming" : scope) ? "bk.start_at ASC" : "bk.start_at DESC";
        return db.sql(SELECT + " WHERE bk.customer_id=:c AND " + cond + " ORDER BY " + order + " LIMIT :l OFFSET :o")
                .param("c", customerId).param("l", Math.min(size, 50)).param("o", Math.max(0, page) * Math.min(size, 50))
                .query().listOfRows().stream().map(r -> decorate(r, false)).toList();
    }

    public List<Map<String, Object>> listForBusiness(long businessId, Long professionalId, LocalDate from, LocalDate to, String status, ZoneId zone) {
        StringBuilder w = new StringBuilder(" WHERE bk.business_id=:b AND bk.start_at >= :f AND bk.start_at < :t");
        if (professionalId != null) w.append(" AND bk.professional_id=:p");
        if (status != null && !status.isBlank()) w.append(" AND bk.status=:st");
        var q = db.sql(SELECT + w + " ORDER BY bk.start_at LIMIT 1000").param("b", businessId)
                .param("f", Timestamp.from(from.atStartOfDay(zone).toInstant())).param("t", Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
        if (professionalId != null) q = q.param("p", professionalId);
        if (status != null && !status.isBlank()) q = q.param("st", status);
        return q.query().listOfRows().stream().map(r -> decorate(r, true)).toList();
    }

    public List<Map<String, Object>> listForProfessional(long professionalId, LocalDate from, LocalDate to, ZoneId zone) {
        var rows = db.sql(SELECT + " WHERE bk.professional_id=:p AND bk.start_at >= :f AND bk.start_at < :t ORDER BY bk.start_at LIMIT 1000").param("p", professionalId)
                .param("f", Timestamp.from(from.atStartOfDay(zone).toInstant())).param("t", Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant())).query().listOfRows();
        return rows.stream().map(r -> decorate(r, true)).toList();
    }

    private static final String SELECT = """
        SELECT bk.id, bk.reference, bk.status, bk.start_at, bk.end_at, bk.price_minor, bk.discount_minor, bk.currency, bk.payment_status,
               bk.customer_note, bk.cancel_reason, bk.created_at, bk.service_id, bk.service_name, bk.business_id,
               b.name AS business_name, b.address, b.city, b.lat, b.lng, b.phone AS business_phone, b.timezone, b.cancellation_hours, b.is_demo,
               bk.professional_id, p.full_name AS professional_name, p.avatar_key AS professional_avatar,
               bk.customer_id, u.full_name AS customer_name, u.phone AS customer_phone,
               (SELECT r.id FROM reviews r WHERE r.booking_id=bk.id) AS review_id
        FROM bookings bk JOIN businesses b ON b.id=bk.business_id JOIN professionals p ON p.id=bk.professional_id JOIN users u ON u.id=bk.customer_id""";

    private Map<String, Object> decorate(Map<String, Object> raw, boolean businessView) {
        Map<String, Object> out = Rows.row(raw);
        Instant now = clock.instant();
        String status = (String) raw.get("status");
        boolean active = status.equals("PENDING") || status.equals("CONFIRMED");
        boolean window = withinFreeWindow(raw, now);
        out.put("totalMinor", ((Number) raw.get("price_minor")).longValue() - ((Number) raw.get("discount_minor")).longValue());
        out.put("canCancel", active && window);
        out.put("canReschedule", active && window);
        out.put("canReview", status.equals("COMPLETED") && raw.get("review_id") == null);
        if (!businessView) { out.remove("customerName"); out.remove("customerPhone"); out.remove("customerId"); }
        else { out.remove("businessPhone"); }
        out.put("history", Rows.rows(db.sql("SELECT from_status, to_status, note, created_at FROM booking_status_history WHERE booking_id=:i ORDER BY id").param("i", raw.get("id")).query().listOfRows()));
        return out;
    }

    // ------------------------------------------------------------------ internals
    private Map<String, Object> lock(long bookingId, Actor actor) {
        var rows = db.sql("""
            SELECT bk.*, b.owner_id, b.cancellation_hours, b.auto_confirm, b.name AS business_name, p.user_id AS pro_user_id
            FROM bookings bk JOIN businesses b ON b.id=bk.business_id JOIN professionals p ON p.id=bk.professional_id
            WHERE bk.id=:i FOR UPDATE OF bk""").param("i", bookingId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Booking");
        var bk = rows.get(0);
        boolean allowed = switch (actor.role()) {
            case "CUSTOMER" -> ((Number) bk.get("customer_id")).longValue() == actor.userId();
            case "BUSINESS_OWNER" -> ((Number) bk.get("owner_id")).longValue() == actor.userId();
            case "PROFESSIONAL" -> bk.get("pro_user_id") != null && ((Number) bk.get("pro_user_id")).longValue() == actor.userId();
            case "PLATFORM_ADMIN" -> true;
            default -> false;
        };
        if (!allowed) throw ApiException.notFound("Booking"); // do not reveal existence
        return bk;
    }

    private boolean withinFreeWindow(Map<String, Object> bk, Instant now) {
        Instant start = ((Timestamp) bk.get("start_at")).toInstant();
        int hours = ((Number) bk.get("cancellation_hours")).intValue();
        return !now.isAfter(start.minus(Duration.ofHours(hours)));
    }

    private void releasePromo(long bookingId) {
        var promo = db.sql("DELETE FROM promotion_redemptions WHERE booking_id=:b RETURNING promotion_id").param("b", bookingId).query(Long.class).optional();
        promo.ifPresent(p -> db.sql("UPDATE promotions SET redemptions=GREATEST(redemptions-1,0) WHERE id=:i").param("i", p).update());
    }

    private void history(long bookingId, String from, String to, Long actor, String note) {
        db.sql("INSERT INTO booking_status_history(booking_id, from_status, to_status, actor_id, note) VALUES (:b,:f,:t,:a,:n)")
          .param("b", bookingId).param("f", from).param("t", to).param("a", actor).param("n", note).update();
    }

    private void notifyBusinessSide(long bookingId, long ownerId, long professionalId, String type, String title, String body) {
        notifications.notify(ownerId, type, title, body, "/business/calendar", "booking:" + bookingId + ":" + type + ":owner:" + System.nanoTime());
        db.sql("SELECT user_id FROM professionals WHERE id=:p AND user_id IS NOT NULL AND user_id <> :o").param("p", professionalId).param("o", ownerId).query(Long.class).optional()
          .ifPresent(u -> notifications.notify(u, type, title, body, "/pro", "booking:" + bookingId + ":" + type + ":pro:" + System.nanoTime()));
    }

    private static String titleFor(String status) {
        return switch (status) {
            case "CONFIRMED" -> "Запись подтверждена";
            case "REJECTED" -> "Запись отклонена";
            case "CANCELLED" -> "Запись отменена салоном";
            case "COMPLETED" -> "Визит завершён — оставьте отзыв";
            case "NO_SHOW" -> "Визит отмечен как пропущенный";
            default -> status;
        };
    }
    private static String actorKind(Actor a) { return a.role().equals("PLATFORM_ADMIN") ? "ADMIN" : a.role(); }
    private static String trim(String s, int max) { return s == null || s.isBlank() ? null : s.trim().substring(0, Math.min(s.trim().length(), max)); }
    private String newReference() {
        StringBuilder sb = new StringBuilder("SL-");
        for (int i = 0; i < 8; i++) sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        return sb.toString();
    }
    static boolean isExclusionViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof java.sql.SQLException s && "23P01".equals(s.getSQLState())) return true;
        return false;
    }
}
