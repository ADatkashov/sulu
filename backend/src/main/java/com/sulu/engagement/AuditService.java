package com.sulu.engagement;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcClient db;
    public AuditService(JdbcClient db) { this.db = db; }

    public void log(Long actorId, String action, String targetType, Object targetId, String detail) {
        db.sql("INSERT INTO audit_logs(actor_id, action, target_type, target_id, detail) VALUES (:a,:ac,:tt,:ti,:d)")
          .param("a", actorId).param("ac", action).param("tt", targetType)
          .param("ti", targetId == null ? null : String.valueOf(targetId))
          .param("d", detail == null ? null : detail.substring(0, Math.min(detail.length(), 400))).update();
    }
}
