package com.sulu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaymentAndReviewTest extends IntegrationTest {

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
    private Res webhook(String body, String sig) throws Exception {
        return post("/api/payments/webhook/simulated", null, body, "X-Sulu-Signature", sig == null ? "" : sig);
    }

    @Test void paymentLifecycleWebhookSignatureAndIdempotency() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long bid = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        Res pay = post("/api/bookings/" + bid + "/payments", c, Map.of("method", "CARD"));
        assertThat(pay.status()).isEqualTo(201);
        assertThat(pay.body().get("simulated").asBoolean()).isTrue();      // always labelled as a simulation
        assertThat(pay.body().get("status").asText()).isEqualTo("PENDING");
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PENDING"); // client "success" is not payment
        String ref = db.sql("SELECT provider_ref FROM payments WHERE id=:i").param("i", pay.body().get("id").asLong()).query(String.class).single();
        String body = "{\"eventId\":\"evt-" + ref + "\",\"type\":\"payment.succeeded\",\"providerRef\":\"" + ref + "\"}";

        assertThat(webhook(body, "deadbeef").status()).isEqualTo(401);                // bad signature
        assertThat(webhook(body, null).status()).isEqualTo(401);
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PENDING");

        Res ok = webhook(body, sign(body));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("duplicate").asBoolean()).isFalse();
        Res dup = webhook(body, sign(body));                                          // redelivery
        assertThat(dup.status()).isEqualTo(200);
        assertThat(dup.body().get("duplicate").asBoolean()).isTrue();
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(db.sql("SELECT count(*) FROM payment_events WHERE event_id=:e").param("e", "evt-" + ref).query(Long.class).single()).isEqualTo(1);
        assertThat(db.sql("SELECT count(*) FROM notifications WHERE type='PAYMENT_SUCCEEDED' AND user_id=:u").param("u", userId(c)).query(Long.class).single()).isEqualTo(1);

        // a late "failed" event cannot undo a succeeded payment
        String late = "{\"eventId\":\"evt2-" + ref + "\",\"type\":\"payment.failed\",\"providerRef\":\"" + ref + "\"}";
        assertThat(webhook(late, sign(late)).status()).isEqualTo(200);
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PAID");
        // cannot pay twice
        assertThat(post("/api/bookings/" + bid + "/payments", c, Map.of("method", "CARD")).status()).isEqualTo(409);

        // cancelling (inside the free window) refunds automatically
        assertThat(post("/api/bookings/" + bid + "/cancel", c, null).status()).isEqualTo(200);
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("REFUNDED");
        assertThat(db.sql("SELECT refunded_minor FROM payments WHERE booking_id=:b").param("b", bid).query(Long.class).single()).isEqualTo(1000000L);
    }

    @Test void simulatedPaymentCanBeFailedAndRetriedAndOtherUsersCannotPay() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long bid = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(post("/api/bookings/" + bid + "/payments", register("CUSTOMER"), Map.of("method", "CARD")).status()).isEqualTo(404);
        assertThat(post("/api/bookings/" + bid + "/payments", c, Map.of("method", "BITCOIN")).status()).isEqualTo(400);
        long p1 = post("/api/bookings/" + bid + "/payments", c, Map.of("method", "CARD")).body().get("id").asLong();
        assertThat(post("/api/payments/" + p1 + "/simulate", c, Map.of("success", false)).body().get("status").asText()).isEqualTo("FAILED");
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("FAILED");
        long p2 = post("/api/bookings/" + bid + "/payments", c, Map.of("method", "APPLE_PAY")).body().get("id").asLong();
        assertThat(post("/api/payments/" + p2 + "/simulate", c, Map.of("success", true)).body().get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test void adminCanPartiallyRefundAndIsAudited() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER"), admin = adminToken();
        long bid = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        long pid = post("/api/bookings/" + bid + "/payments", c, Map.of("method", "CARD")).body().get("id").asLong();
        post("/api/payments/" + pid + "/simulate", c, Map.of("success", true));
        assertThat(post("/api/payments/" + pid + "/refund", c, Map.of("amountMinor", 1)).status()).isIn(403, 404, 405);
        Res r = post("/api/admin/payments/" + pid + "/refund", admin, Map.of("amountMinor", 300000));
        assertThat(r.body().get("status").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(post("/api/admin/payments/" + pid + "/refund", admin, Map.of("amountMinor", 900000)).status()).isEqualTo(400);  // exceeds the remainder
        assertThat(get("/api/bookings/" + bid, c).body().get("paymentStatus").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(db.sql("SELECT count(*) FROM audit_logs WHERE action='REFUND' AND target_id=:i").param("i", String.valueOf(pid)).query(Long.class).single()).isEqualTo(1);
    }

    @Test void reviewsRequireCompletedOwnBookingAndOnlyOnce() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER"), stranger = register("CUSTOMER");
        long cid = userId(c);
        long upcoming = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(post("/api/bookings/" + upcoming + "/review", c, Map.of("rating", 5, "body", "too early")).status()).isEqualTo(422);
        long past = insertBooking(cid, f, java.time.Instant.now().minusSeconds(86400), "COMPLETED");
        assertThat(post("/api/bookings/" + past + "/review", stranger, Map.of("rating", 5)).status()).isEqualTo(404);       // not their booking
        assertThat(post("/api/bookings/" + past + "/review", c, Map.of("rating", 6)).status()).isEqualTo(400);              // out of range
        assertThat(post("/api/bookings/" + past + "/review", c, Map.of("rating", 0)).status()).isEqualTo(400);
        assertThat(post("/api/bookings/" + past + "/review", c, Map.of("rating", 4, "body", "Nice")).status()).isEqualTo(201);
        Res dup = post("/api/bookings/" + past + "/review", c, Map.of("rating", 1, "body", "again"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.body().get("code").asText()).isEqualTo("REVIEW_EXISTS");
        // summary is calculated server-side
        long past2 = insertBooking(cid, f, java.time.Instant.now().minusSeconds(172800), "COMPLETED");
        assertThat(post("/api/bookings/" + past2 + "/review", c, Map.of("rating", 5)).status()).isEqualTo(201);
        Res list = get("/api/reviews?businessId=" + f.businessId(), null);
        assertThat(list.body().get("count").asInt()).isEqualTo(2);
        assertThat(list.body().get("average").asDouble()).isEqualTo(4.5);
        assertThat(get("/api/businesses/" + f.businessId(), null).body().get("ratingAvg").asDouble()).isEqualTo(4.5);
        assertThat(list.body().get("items").get(0).get("author").asText()).doesNotContain("Test CUSTOMER".substring(5)); // only "First L." is public
        // owner reply and moderation
        long reviewId = list.body().get("items").get(0).get("id").asLong();
        assertThat(post("/api/reviews/" + reviewId + "/reply", f.ownerToken(), Map.of("reply", "Thank you!")).status()).isEqualTo(200);
        assertThat(post("/api/reviews/" + reviewId + "/reply", stranger, Map.of("reply", "x")).status()).isEqualTo(403);
        assertThat(post("/api/reviews/" + reviewId + "/report", stranger, Map.of("reason", "spam")).status()).isEqualTo(200);
        assertThat(post("/api/reviews/" + reviewId + "/report", stranger, Map.of("reason", "spam")).status()).isEqualTo(200); // idempotent
        assertThat(db.sql("SELECT reported_count FROM reviews WHERE id=:i").param("i", reviewId).query(Integer.class).single()).isEqualTo(1);
        String admin = adminToken();
        assertThat(post("/api/admin/reviews/" + reviewId + "/status", admin, Map.of("status", "HIDDEN")).status()).isEqualTo(200);
        assertThat(get("/api/reviews?businessId=" + f.businessId(), null).body().get("count").asInt()).isEqualTo(1);
        assertThat(get("/api/businesses/" + f.businessId(), null).body().get("ratingCount").asInt()).isEqualTo(1);
    }

    @Test void favoritesPersistAndAreScopedToTheUser() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER"), other = register("CUSTOMER");
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/favorites/business/" + f.businessId()), c, null).status()).isEqualTo(200);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/favorites/professional/" + f.professionalId()), c, null);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/favorites/business/" + f.businessId()), c, null); // idempotent
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/favorites/business/99999999"), c, null).status()).isEqualTo(404);
        Res fav = get("/api/favorites", c);
        assertThat(fav.body().get("businesses").size()).isEqualTo(1);
        assertThat(fav.body().get("professionals").size()).isEqualTo(1);
        assertThat(get("/api/favorites", other).body().get("businesses").size()).isZero();
        assertThat(get("/api/businesses/" + f.businessId(), c).body().get("favorite").asBoolean()).isTrue();
        assertThat(get("/api/businesses/" + f.businessId(), other).body().get("favorite").asBoolean()).isFalse();
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/favorites/business/" + f.businessId()), c, null);
        assertThat(get("/api/favorites", c).body().get("businesses").size()).isZero();
    }

    @Test void notificationsAreCreatedDeduplicatedAndReadable() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long bid = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        Res n = get("/api/notifications", c);
        assertThat(n.body().get("unread").asInt()).isEqualTo(1);
        assertThat(n.body().get("items").get(0).get("type").asText()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(get("/api/notifications", f.ownerToken()).body().get("items").get(0).get("type").asText()).isEqualTo("BOOKING_NEW");
        long nid = n.body().get("items").get(0).get("id").asLong();
        assertThat(post("/api/notifications/" + nid + "/read", register("CUSTOMER"), null).status()).isEqualTo(404);
        assertThat(post("/api/notifications/" + nid + "/read", c, null).status()).isEqualTo(200);
        assertThat(get("/api/notifications", c).body().get("unread").asInt()).isZero();
        assertThat(bid).isPositive();
    }

    @Test void uploadsAreValidatedByContentNotFilename() throws Exception {
        Fixture f = fixture(true, 24, 0);
        var evil = new org.springframework.mock.web.MockMultipartFile("file", "shell.png", "image/png", "<?php echo 1;".getBytes());
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/biz/" + f.businessId() + "/professionals/" + f.professionalId() + "/portfolio").file(evil)
                .header("Authorization", "Bearer " + f.ownerToken());
        assertThat(mvc.perform(req).andReturn().getResponse().getStatus()).isEqualTo(400);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var good = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/biz/" + f.businessId() + "/professionals/" + f.professionalId() + "/portfolio")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "../../etc/passwd", "image/png", png)).header("Authorization", "Bearer " + f.ownerToken());
        var res = mvc.perform(good).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(201);
        String key = json.readTree(res.getContentAsString()).get("imageKey").asText();
        assertThat(key).matches("[0-9a-f\\-]{36}\\.png"); // generated key; the client filename is never used
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/media/" + key)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/media/..%2F..%2Fetc%2Fpasswd")).andReturn().getResponse().getStatus()).isIn(400, 404); // traversal is refused
    }
}
