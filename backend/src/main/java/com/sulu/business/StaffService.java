package com.sulu.business;

import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Professional schedule management shared by owners (any staff in their business) and professionals (themselves). */
@Service
public class StaffService {
    public record Hours(int weekday, String startTime, String endTime, String breakStart, String breakEnd) {}
    private final JdbcClient db;
    public StaffService(JdbcClient db) { this.db = db; }

    private static LocalTime t(String s, String field) {
        try { return LocalTime.parse(s); } catch (Exception e) { throw ApiException.bad("BAD_TIME", field + " must be HH:mm"); }
    }

    @Transactional
    public void setHours(long professionalId, List<Hours> hours) {
        Set<Integer> seen = new HashSet<>();
        for (Hours h : hours) {
            if (h.weekday() < 1 || h.weekday() > 7 || !seen.add(h.weekday())) throw ApiException.bad("BAD_WEEKDAY", "weekday must be unique and 1..7");
            LocalTime s = t(h.startTime(), "startTime"), e = t(h.endTime(), "endTime");
            if (!e.isAfter(s)) throw ApiException.bad("BAD_HOURS", "endTime must be after startTime");
            if ((h.breakStart() == null) != (h.breakEnd() == null)) throw ApiException.bad("BAD_BREAK", "break needs both start and end");
            if (h.breakStart() != null) {
                LocalTime bs = t(h.breakStart(), "breakStart"), be = t(h.breakEnd(), "breakEnd");
                if (!be.isAfter(bs) || bs.isBefore(s) || be.isAfter(e)) throw ApiException.bad("BAD_BREAK", "break must be inside working hours");
            }
        }
        db.sql("DELETE FROM professional_working_hours WHERE professional_id=:p").param("p", professionalId).update();
        for (Hours h : hours)
            db.sql("INSERT INTO professional_working_hours(professional_id, weekday, start_time, end_time, break_start, break_end) VALUES (:p,:w,:s,:e,:bs,:be)")
              .param("p", professionalId).param("w", h.weekday()).param("s", java.sql.Time.valueOf(t(h.startTime(), "").withSecond(0)))
              .param("e", java.sql.Time.valueOf(t(h.endTime(), "").withSecond(0)))
              .param("bs", h.breakStart() == null ? null : java.sql.Time.valueOf(t(h.breakStart(), "")))
              .param("be", h.breakEnd() == null ? null : java.sql.Time.valueOf(t(h.breakEnd(), ""))).update();
    }

    public List<Map<String, Object>> hours(long professionalId) {
        return Rows.rows(db.sql("SELECT weekday, start_time, end_time, break_start, break_end FROM professional_working_hours WHERE professional_id=:p ORDER BY weekday").param("p", professionalId).query().listOfRows());
    }

    public Map<String, Object> addTimeOff(long professionalId, Instant from, Instant to, String reason) {
        if (!to.isAfter(from)) throw ApiException.bad("BAD_RANGE", "End must be after start");
        long id = db.sql("INSERT INTO professional_time_off(professional_id, starts_at, ends_at, reason) VALUES (:p,:s,:e,:r) RETURNING id")
                .param("p", professionalId).param("s", Timestamp.from(from)).param("e", Timestamp.from(to)).param("r", reason).query(Long.class).single();
        long conflicts = db.sql("SELECT count(*) FROM bookings WHERE professional_id=:p AND status IN ('PENDING','CONFIRMED') AND start_at < :e AND end_at > :s")
                .param("p", professionalId).param("s", Timestamp.from(from)).param("e", Timestamp.from(to)).query(Long.class).single();
        return Map.of("id", id, "conflictingBookings", conflicts);
    }

    public void removeTimeOff(long professionalId, long id) {
        if (db.sql("DELETE FROM professional_time_off WHERE id=:i AND professional_id=:p").param("i", id).param("p", professionalId).update() == 0) throw ApiException.notFound("Time off");
    }

    public List<Map<String, Object>> timeOff(long professionalId) {
        return Rows.rows(db.sql("SELECT id, starts_at, ends_at, reason FROM professional_time_off WHERE professional_id=:p AND ends_at > now() ORDER BY starts_at").param("p", professionalId).query().listOfRows());
    }
}
