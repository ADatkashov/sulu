package com.sulu.booking;

import com.sulu.common.ApiException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Computes bookable start times. Accounts for: business hours + closures, professional hours / breaks / time off,
 * service duration, existing PENDING|CONFIRMED bookings (+ buffer), lead time, advance limit, and the business time zone.
 * Results are advisory — {@link BookingService} re-validates inside the booking transaction.
 */
@Service
public class AvailabilityService {
    public static final int STEP_MINUTES = 15;

    public record Slot(Instant start, Instant end, long professionalId) {}
    public record Context(long businessId, ZoneId zone, int leadMinutes, int maxAdvanceDays, int bufferMinutes, int durationMin) {}

    private final JdbcClient db;
    private final Clock clock;
    public AvailabilityService(JdbcClient db, Clock clock) { this.db = db; this.clock = clock; }

    /** Loads rules for a (service, professional) pair, verifying they belong together and are bookable. */
    public Context context(long serviceId, long professionalId) {
        var row = db.sql("""
            SELECT b.id AS bid, b.timezone, b.min_lead_minutes, b.max_advance_days, b.buffer_minutes, s.duration_min
            FROM services s JOIN businesses b ON b.id=s.business_id
            JOIN professionals p ON p.business_id=b.id
            JOIN professional_services ps ON ps.professional_id=p.id AND ps.service_id=s.id
            WHERE s.id=:s AND p.id=:p AND s.active AND p.active AND b.status='APPROVED'""")
            .param("s", serviceId).param("p", professionalId).query().listOfRows().stream().findFirst()
            .orElseThrow(() -> ApiException.notFound("Service/professional combination"));
        return new Context(((Number) row.get("bid")).longValue(), ZoneId.of((String) row.get("timezone")),
                ((Number) row.get("min_lead_minutes")).intValue(), ((Number) row.get("max_advance_days")).intValue(),
                ((Number) row.get("buffer_minutes")).intValue(), ((Number) row.get("duration_min")).intValue());
    }

    public List<Long> eligibleProfessionals(long serviceId) {
        return db.sql("SELECT p.id FROM professional_services ps JOIN professionals p ON p.id=ps.professional_id WHERE ps.service_id=:s AND p.active ORDER BY p.id")
                .param("s", serviceId).query(Long.class).list();
    }

    /** Slots for one professional on a local calendar date. {@code excludeBookingId} lets a booking be moved onto its own time. */
    public List<Slot> slots(long serviceId, long professionalId, LocalDate date, Long excludeBookingId) {
        Context c = context(serviceId, professionalId);
        return slots(c, professionalId, date, excludeBookingId);
    }

    public List<Slot> slots(Context c, long professionalId, LocalDate date, Long excludeBookingId) {
        ZoneId zone = c.zone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (date.isBefore(today) || date.isAfter(today.plusDays(c.maxAdvanceDays()))) return List.of();
        if (!db.sql("SELECT count(*) FROM business_closures WHERE business_id=:b AND closed_on=:d").param("b", c.businessId()).param("d", java.sql.Date.valueOf(date)).query(Long.class).single().equals(0L)) return List.of();

        int weekday = date.getDayOfWeek().getValue();
        var bh = db.sql("SELECT open_time, close_time FROM business_hours WHERE business_id=:b AND weekday=:w").param("b", c.businessId()).param("w", weekday).query().listOfRows();
        var ph = db.sql("SELECT start_time, end_time, break_start, break_end FROM professional_working_hours WHERE professional_id=:p AND weekday=:w").param("p", professionalId).param("w", weekday).query().listOfRows();
        if (bh.isEmpty() || ph.isEmpty()) return List.of();

        LocalTime open = max(lt(bh.get(0).get("open_time")), lt(ph.get(0).get("start_time")));
        LocalTime close = min(lt(bh.get(0).get("close_time")), lt(ph.get(0).get("end_time")));
        LocalTime bs = lt(ph.get(0).get("break_start")), be = lt(ph.get(0).get("break_end"));
        if (!close.isAfter(open)) return List.of();

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant earliest = now.plus(Duration.ofMinutes(c.leadMinutes()));
        Duration dur = Duration.ofMinutes(c.durationMin());
        Duration buf = Duration.ofMinutes(c.bufferMinutes());

        // busy intervals (existing bookings are widened by the buffer on both sides)
        List<Instant[]> busy = new ArrayList<>();
        String exclude = excludeBookingId == null ? "" : " AND id <> :ex";
        var q = db.sql("SELECT start_at, end_at FROM bookings WHERE professional_id=:p AND status IN ('PENDING','CONFIRMED') AND start_at < :de AND end_at > :ds" + exclude)
                .param("p", professionalId).param("de", Timestamp.from(dayEnd.plus(buf))).param("ds", Timestamp.from(dayStart.minus(buf)));
        if (excludeBookingId != null) q = q.param("ex", excludeBookingId);
        for (var r : q.query().listOfRows())
            busy.add(new Instant[]{((Timestamp) r.get("start_at")).toInstant().minus(buf), ((Timestamp) r.get("end_at")).toInstant().plus(buf)});
        for (var r : db.sql("SELECT starts_at, ends_at FROM professional_time_off WHERE professional_id=:p AND starts_at < :de AND ends_at > :ds")
                .param("p", professionalId).param("de", Timestamp.from(dayEnd)).param("ds", Timestamp.from(dayStart)).query().listOfRows())
            busy.add(new Instant[]{((Timestamp) r.get("starts_at")).toInstant(), ((Timestamp) r.get("ends_at")).toInstant()});
        if (bs != null) busy.add(new Instant[]{date.atTime(bs).atZone(zone).toInstant(), date.atTime(be).atZone(zone).toInstant()});

        List<Slot> out = new ArrayList<>();
        Instant windowEnd = date.atTime(close).atZone(zone).toInstant();
        for (LocalTime t = open; !t.isAfter(close); t = t.plusMinutes(STEP_MINUTES)) {
            Instant s = date.atTime(t).atZone(zone).toInstant();
            Instant e = s.plus(dur);
            if (e.isAfter(windowEnd)) break;
            if (s.isBefore(earliest)) continue;
            boolean clash = false;
            for (Instant[] b : busy) if (s.isBefore(b[1]) && e.isAfter(b[0])) { clash = true; break; }
            if (!clash) out.add(new Slot(s, e, professionalId));
            if (t.plusMinutes(STEP_MINUTES).isBefore(t)) break; // wrapped past midnight
        }
        return out;
    }

    /** "Any professional": union of eligible professionals' slots; each start time is assigned to one free professional. */
    public List<Slot> slotsAny(long serviceId, LocalDate date) {
        Map<Instant, Slot> merged = new TreeMap<>();
        for (long pid : eligibleProfessionals(serviceId)) {
            Context c;
            try { c = context(serviceId, pid); } catch (ApiException e) { continue; }
            for (Slot s : slots(c, pid, date, null)) merged.putIfAbsent(s.start(), s);
        }
        return new ArrayList<>(merged.values());
    }

    /** First available slot in the next {@code horizonDays} days (used for "next available" on cards/details). */
    public Optional<Slot> next(long serviceId, Long professionalId, int horizonDays) {
        ZoneId zone;
        List<Long> pros = professionalId == null ? eligibleProfessionals(serviceId) : List.of(professionalId);
        Slot best = null;
        for (long pid : pros) {
            Context c;
            try { c = context(serviceId, pid); } catch (ApiException e) { continue; }
            zone = c.zone();
            LocalDate d = LocalDate.ofInstant(clock.instant(), zone);
            for (int i = 0; i <= horizonDays && i <= c.maxAdvanceDays(); i++) {
                List<Slot> s = slots(c, pid, d.plusDays(i), null);
                if (!s.isEmpty()) { if (best == null || s.get(0).start().isBefore(best.start())) best = s.get(0); break; }
            }
        }
        return Optional.ofNullable(best);
    }

    private static LocalTime lt(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Time t) return t.toLocalTime();
        return LocalTime.parse(o.toString());
    }
    private static LocalTime max(LocalTime a, LocalTime b) { return a.isAfter(b) ? a : b; }
    private static LocalTime min(LocalTime a, LocalTime b) { return a.isBefore(b) ? a : b; }
}
