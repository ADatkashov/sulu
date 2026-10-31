package com.sulu.engagement;

import com.sulu.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final JdbcClient db;
    private final NotificationService notifications;
    public ReviewService(JdbcClient db, NotificationService notifications) { this.db = db; this.notifications = notifications; }

    /** Server-side eligibility: the booking must be the caller's, COMPLETED, and not yet reviewed (also enforced by UNIQUE(booking_id)). */
    @Transactional
    public long create(long customerId, long bookingId, int rating, String body) {
        var bk = db.sql("SELECT bk.id, bk.status, bk.business_id, bk.professional_id, bk.service_name, b.owner_id FROM bookings bk JOIN businesses b ON b.id=bk.business_id WHERE bk.id=:i AND bk.customer_id=:c FOR UPDATE OF bk")
                .param("i", bookingId).param("c", customerId).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Booking"));
        if (!"COMPLETED".equals(bk.get("status"))) throw ApiException.unprocessable("REVIEW_NOT_ELIGIBLE", "Only completed appointments can be reviewed");
        long id;
        try {
            id = db.sql("INSERT INTO reviews(booking_id, customer_id, business_id, professional_id, rating, body) VALUES (:b,:c,:bs,:p,:r,:t) RETURNING id")
                    .param("b", bookingId).param("c", customerId).param("bs", bk.get("business_id")).param("p", bk.get("professional_id")).param("r", rating)
                    .param("t", body == null ? "" : body.trim()).query(Long.class).single();
        } catch (DuplicateKeyException e) { throw ApiException.conflict("REVIEW_EXISTS", "This appointment has already been reviewed"); }
        recalc(((Number) bk.get("business_id")).longValue(), ((Number) bk.get("professional_id")).longValue());
        notifications.notify(((Number) bk.get("owner_id")).longValue(), "REVIEW_NEW", "Новый отзыв", rating + "★ · " + bk.get("service_name"), "/business/reviews", "review:" + id);
        return id;
    }

    public void recalc(long businessId, long professionalId) {
        db.sql("""
            UPDATE businesses SET rating_avg=COALESCE((SELECT round(avg(rating)::numeric,2) FROM reviews WHERE business_id=:b AND status='VISIBLE'),0),
                                  rating_count=(SELECT count(*) FROM reviews WHERE business_id=:b AND status='VISIBLE') WHERE id=:b""").param("b", businessId).update();
        db.sql("""
            UPDATE professionals SET rating_avg=COALESCE((SELECT round(avg(rating)::numeric,2) FROM reviews WHERE professional_id=:p AND status='VISIBLE'),0),
                                     rating_count=(SELECT count(*) FROM reviews WHERE professional_id=:p AND status='VISIBLE') WHERE id=:p""").param("p", professionalId).update();
    }

    @Transactional
    public void reply(long ownerId, long reviewId, String text) {
        int n = db.sql("UPDATE reviews r SET reply=:t, replied_at=now() FROM businesses b WHERE r.id=:i AND b.id=r.business_id AND b.owner_id=:o")
                .param("t", text.trim()).param("i", reviewId).param("o", ownerId).update();
        if (n == 0) throw ApiException.notFound("Review");
    }

    @Transactional
    public void report(long userId, long reviewId, String reason) {
        if (db.sql("SELECT count(*) FROM reviews WHERE id=:i").param("i", reviewId).query(Long.class).single() == 0) throw ApiException.notFound("Review");
        try { db.sql("INSERT INTO review_reports(review_id, reporter_id, reason) VALUES (:r,:u,:t)").param("r", reviewId).param("u", userId).param("t", reason).update(); }
        catch (DuplicateKeyException e) { return; }
        db.sql("UPDATE reviews SET reported_count=reported_count+1 WHERE id=:i").param("i", reviewId).update();
    }

    @Transactional
    public void setStatus(long reviewId, String status) {
        var r = db.sql("SELECT business_id, professional_id FROM reviews WHERE id=:i FOR UPDATE").param("i", reviewId).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Review"));
        db.sql("UPDATE reviews SET status=:s WHERE id=:i").param("s", status).param("i", reviewId).update();
        recalc(((Number) r.get("business_id")).longValue(), ((Number) r.get("professional_id")).longValue());
    }
}
