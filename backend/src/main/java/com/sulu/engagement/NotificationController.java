package com.sulu.engagement;

import com.sulu.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService svc;
    public NotificationController(NotificationService svc) { this.svc = svc; }

    @GetMapping
    public Map<String, Object> list() {
        long u = CurrentUser.require().id();
        return Map.of("items", svc.list(u, 50), "unread", svc.unread(u));
    }
    @PostMapping("/{id}/read") public void read(@PathVariable long id) { svc.markRead(CurrentUser.require().id(), id); }
    @PostMapping("/read-all") public void readAll() { svc.markAllRead(CurrentUser.require().id()); }
}
