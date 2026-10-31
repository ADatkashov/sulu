package com.sulu.booking;

import com.sulu.common.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {
    private final AvailabilityService svc;
    private final org.springframework.jdbc.core.simple.JdbcClient db;
    public AvailabilityController(AvailabilityService svc, org.springframework.jdbc.core.simple.JdbcClient db) { this.svc = svc; this.db = db; }

    @GetMapping
    public Map<String, Object> slots(@RequestParam long serviceId, @RequestParam(required = false) Long professionalId, @RequestParam String date) {
        LocalDate d;
        try { d = LocalDate.parse(date); } catch (DateTimeParseException e) { throw ApiException.bad("BAD_DATE", "date must be yyyy-MM-dd"); }
        String tz = db.sql("SELECT b.timezone FROM services s JOIN businesses b ON b.id=s.business_id WHERE s.id=:s AND s.active AND b.status='APPROVED'")
                .param("s", serviceId).query(String.class).optional().orElseThrow(() -> ApiException.notFound("Service"));
        List<AvailabilityService.Slot> slots = professionalId == null ? svc.slotsAny(serviceId, d) : svc.slots(serviceId, professionalId, d, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", d.toString()); out.put("timezone", tz);
        out.put("slots", slots.stream().map(s -> Map.of("start", s.start(), "end", s.end(), "professionalId", s.professionalId())).toList());
        return out;
    }

    @GetMapping("/next")
    public Map<String, Object> next(@RequestParam long serviceId, @RequestParam(required = false) Long professionalId) {
        return svc.next(serviceId, professionalId, 30)
                .<Map<String, Object>>map(s -> Map.of("start", (Object) s.start(), "professionalId", s.professionalId()))
                .orElse(Map.of());
    }
}
