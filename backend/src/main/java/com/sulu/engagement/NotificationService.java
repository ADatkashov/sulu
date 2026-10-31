package com.sulu.engagement;

import com.sulu.common.ApiException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JdbcClient db;
    private final List<ExternalChannel> channels;

    public NotificationService(JdbcClient db, List<ExternalChannel> channels) { this.db = db; this.channels = channels; }

    /** In-app notification (always) + external channels (only those actually configured). Duplicate dedupeKey is ignored.
     *  Bodies deliberately avoid private customer details. */
    public void notify(long userId, String type, String title, String body, String link, String dedupeKey) {
        int n = db.sql("""
            INSERT INTO notifications(user_id, type, title, body, link, dedupe_key) VALUES (:u,:t,:ti,:b,:l,:d)
            ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING""")
            .param("u", userId).param("t", type).param("ti", title).param("b", body == null ? "" : body)
            .param("l", link).param("d", dedupeKey).update();
        if (n == 0) return;
        for (ExternalChannel c : channels) {
            if (!c.configured()) continue;
            try { c.send(userId, title, body); } catch (Exception e) { log.warn("channel {} failed: {}", c.name(), e.getMessage()); }
        }
    }

    public List<Map<String, Object>> list(long userId, int limit) {
        return db.sql("SELECT id, type, title, body, link, read_at IS NOT NULL AS read, created_at FROM notifications WHERE user_id=:u ORDER BY created_at DESC, id DESC LIMIT :l")
                .param("u", userId).param("l", Math.min(limit, 100)).query().listOfRows();
    }

    public long unread(long userId) {
        return db.sql("SELECT count(*) FROM notifications WHERE user_id=:u AND read_at IS NULL").param("u", userId).query(Long.class).single();
    }

    public void markRead(long userId, long id) {
        int n = db.sql("UPDATE notifications SET read_at=now() WHERE id=:i AND user_id=:u").param("i", id).param("u", userId).update();
        if (n == 0) throw ApiException.notFound("Notification");
    }

    public void markAllRead(long userId) {
        db.sql("UPDATE notifications SET read_at=now() WHERE user_id=:u AND read_at IS NULL").param("u", userId).update();
    }
}
