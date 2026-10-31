package com.sulu.business;

import com.sulu.common.ApiException;
import com.sulu.security.CurrentUser;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Tenant isolation: every /api/biz/{id}/** call goes through here. An unrelated business looks like "not found". */
@Component
public class BusinessAccess {
    private final JdbcClient db;
    public BusinessAccess(JdbcClient db) { this.db = db; }

    public record Biz(long id, ZoneId zone, String currency) {}

    public Biz owned(long businessId) {
        var u = CurrentUser.require();
        Map<String, Object> r = db.sql("SELECT id, timezone, currency FROM businesses WHERE id=:i AND owner_id=:o").param("i", businessId).param("o", u.id())
                .query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Business"));
        return new Biz(businessId, ZoneId.of((String) r.get("timezone")), ((String) r.get("currency")).trim());
    }

    public void professionalInBusiness(long businessId, long professionalId) {
        if (db.sql("SELECT count(*) FROM professionals WHERE id=:p AND business_id=:b").param("p", professionalId).param("b", businessId).query(Long.class).single() == 0)
            throw ApiException.notFound("Professional");
    }

    public void serviceInBusiness(long businessId, long serviceId) {
        if (db.sql("SELECT count(*) FROM services WHERE id=:s AND business_id=:b").param("s", serviceId).param("b", businessId).query(Long.class).single() == 0)
            throw ApiException.notFound("Service");
    }
}
