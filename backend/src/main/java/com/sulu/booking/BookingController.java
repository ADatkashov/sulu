package com.sulu.booking;

import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {
    private final BookingService svc;
    public BookingController(BookingService svc) { this.svc = svc; }

    public record CreateReq(@NotNull Long serviceId, Long professionalId, @NotNull Instant startAt, @Size(max = 500) String note, @Size(max = 40) String promoCode) {}
    public record RescheduleReq(@NotNull Instant startAt) {}
    public record CancelReq(@Size(max = 200) String reason) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateReq r, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return svc.create(CurrentUser.require().id(), new BookingService.CreateRequest(r.serviceId(), r.professionalId(), r.startAt(), r.note(), r.promoCode()), key);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "upcoming") String scope, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return svc.listForCustomer(CurrentUser.require().id(), scope, page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) { return svc.viewForCustomer(id, CurrentUser.require().id()); }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelReq r) {
        var u = CurrentUser.require();
        return svc.act(new BookingService.Actor(u.id(), u.role()), id, "cancel", r == null ? null : r.reason());
    }

    @PostMapping("/{id}/reschedule")
    public Map<String, Object> reschedule(@PathVariable long id, @Valid @RequestBody RescheduleReq r) {
        var u = CurrentUser.require();
        return svc.reschedule(new BookingService.Actor(u.id(), u.role()), id, r.startAt());
    }
}
