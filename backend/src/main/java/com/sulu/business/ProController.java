package com.sulu.business;

import com.sulu.booking.BookingService;
import com.sulu.common.ApiException;
import com.sulu.common.Rows;
import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

/** Self-service for users with the PROFESSIONAL role: only their own profile, schedule and appointments. */
@RestController
@RequestMapping("/api/pro")
public class ProController {
    private final JdbcClient db; private final StaffService staff; private final BookingService bookings; private final Clock clock;
    public ProController(JdbcClient db, StaffService staff, BookingService bookings, Clock clock) { this.db = db; this.staff = staff; this.bookings = bookings; this.clock = clock; }

    public record ProfileReq(@Size(max = 120) String title, @Size(max = 4000) String bio, @Size(max = 300) String specialties) {}
    public record TimeOffReq(@NotNull Instant from, @NotNull Instant to, @Size(max = 120) String reason) {}
    public record ActionReq(@Size(max = 200) String reason) {}

    private long myId() {
        return db.sql("SELECT id FROM professionals WHERE user_id=:u AND active").param("u", CurrentUser.require().id()).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Professional profile"));
    }
    private ZoneId zone(long pid) { return ZoneId.of(db.sql("SELECT b.timezone FROM professionals p JOIN businesses b ON b.id=p.business_id WHERE p.id=:p").param("p", pid).query(String.class).single()); }

    @GetMapping("/me")
    public Map<String, Object> me() {
        long pid = myId();
        var p = Rows.row(db.sql("SELECT p.id, p.full_name, p.title, p.bio, p.specialties, p.experience_years, p.avatar_key, p.rating_avg, p.rating_count, b.name AS business_name, b.timezone FROM professionals p JOIN businesses b ON b.id=p.business_id WHERE p.id=:i").param("i", pid).query().listOfRows().get(0));
        p.put("hours", staff.hours(pid)); p.put("timeOff", staff.timeOff(pid));
        return p;
    }

    @PutMapping("/me")
    public void update(@Valid @RequestBody ProfileReq r) {
        db.sql("UPDATE professionals SET title=COALESCE(:t,title), bio=COALESCE(:b,bio), specialties=COALESCE(:s,specialties) WHERE id=:i").param("t", r.title()).param("b", r.bio()).param("s", r.specialties()).param("i", myId()).update();
    }

    @PutMapping("/me/hours")
    public void hours(@RequestBody List<StaffService.Hours> hours) { staff.setHours(myId(), hours); }

    @PostMapping("/me/time-off")
    public Map<String, Object> addTimeOff(@Valid @RequestBody TimeOffReq r) { return staff.addTimeOff(myId(), r.from(), r.to(), r.reason()); }

    @DeleteMapping("/me/time-off/{id}")
    public void removeTimeOff(@PathVariable long id) { staff.removeTimeOff(myId(), id); }

    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        long pid = myId(); ZoneId z = zone(pid);
        LocalDate f = from != null ? from : LocalDate.now(clock.withZone(z));
        LocalDate t = to != null ? to : f.plusDays(7);
        if (t.isBefore(f) || f.plusDays(62).isBefore(t)) throw ApiException.bad("BAD_RANGE", "Range must be 1..62 days");
        return bookings.listForProfessional(pid, f, t, z);
    }

    /** Accept / reject / complete / no-show on own appointments (booking policy decides whether acceptance is needed). */
    @PostMapping("/bookings/{id}/{action}")
    public Map<String, Object> action(@PathVariable long id, @PathVariable String action, @RequestBody(required = false) ActionReq r) {
        var u = CurrentUser.require();
        return bookings.act(new BookingService.Actor(u.id(), u.role()), id, action, r == null ? null : r.reason());
    }
}
