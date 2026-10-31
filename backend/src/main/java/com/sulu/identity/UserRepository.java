package com.sulu.identity;

import com.sulu.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class UserRepository {
    private UserRepository() {}

    public static UserDto get(JdbcClient db, long id) {
        return db.sql("SELECT id, email, full_name, phone, role, language, city, notify_email, notify_push FROM users WHERE id=:i").param("i", id)
                .query((rs, n) -> new UserDto(rs.getLong("id"), rs.getString("email"), rs.getString("full_name"), rs.getString("phone"),
                        rs.getString("role"), rs.getString("language"), rs.getString("city"), rs.getBoolean("notify_email"), rs.getBoolean("notify_push")))
                .optional().orElseThrow(() -> ApiException.notFound("User"));
    }
}
