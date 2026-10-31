package com.sulu.booking;

import com.sulu.engagement.NotificationService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates in-app reminders for appointments starting in the next 24h (idempotent via dedupe key). */
@Component
public class ReminderJob {
    private final JdbcClient db; private final NotificationService notifications; private final Clock clock;
    public ReminderJob(JdbcClient db, NotificationService n, Clock clock) { this.db = db; this.notifications = n; this.clock = clock; }

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void run() {
        var now = clock.instant();
        for (var r : db.sql("""
                SELECT bk.id, bk.customer_id, bk.service_name, b.name AS business_name FROM bookings bk JOIN businesses b ON b.id=bk.business_id
                WHERE bk.status='CONFIRMED' AND bk.start_at > :n AND bk.start_at <= :h""")
                .param("n", Timestamp.from(now)).param("h", Timestamp.from(now.plus(Duration.ofHours(24)))).query().listOfRows()) {
            notifications.notify(((Number) r.get("customer_id")).longValue(), "BOOKING_REMINDER", "Напоминание о записи",
                    r.get("business_name") + " · " + r.get("service_name"), "/bookings/" + r.get("id"), "reminder:" + r.get("id"));
        }
    }
}
