package com.sulu;

import static org.assertj.core.api.Assertions.assertThat;

import com.sulu.booking.BookingService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingEngineTest extends IntegrationTest {
    @Autowired BookingService bookingService;

    private List<String> slotStarts(Fixture f, int daysAhead) throws Exception {
        String date = LocalDate.now(ALMATY).plusDays(daysAhead).toString();
        Res r = get("/api/availability?serviceId=" + f.serviceId() + "&professionalId=" + f.professionalId() + "&date=" + date, null);
        List<String> out = new ArrayList<>();
        r.body().get("slots").forEach(s -> out.add(Instant.parse(s.get("start").asText()).atZone(ALMATY).toLocalTime().toString()));
        return out;
    }

    @Test void slotsRespectHoursBreakAndServiceDuration() throws Exception {
        Fixture f = fixture(true, 24, 0);
        List<String> s = slotStarts(f, 3);
        assertThat(s.get(0)).isEqualTo("09:00");
        assertThat(s).contains("12:00");                       // 12:00–13:00 ends exactly at the break start
        assertThat(s).doesNotContain("12:15", "12:45", "13:00", "13:45"); // would overlap the 13:00–14:00 break
        assertThat(s).contains("14:00");
        assertThat(s.get(s.size() - 1)).isEqualTo("20:00");   // last 60-min slot ending at 21:00
        assertThat(s).doesNotContain("20:15");
    }

    @Test void existingBookingsBufferTimeOffClosuresAndLeadTimeAreHonoured() throws Exception {
        Fixture f = fixture(true, 24, 30); // 30 min buffer
        String c = register("CUSTOMER");
        assertThat(book(c, f, at(3, 10, 0)).status()).isEqualTo(201);
        List<String> s = slotStarts(f, 3);
        // booked 10:00–11:00 with a 30 min buffer on each side => nothing may start in [08:30, 11:30)
        assertThat(s).doesNotContain("09:00", "09:15", "09:30", "10:00", "10:30", "10:45", "11:00", "11:15");
        assertThat(s.get(0)).isEqualTo("11:30");
        // time off
        Instant from = at(4, 15, 0), to = at(4, 17, 0);
        assertThat(post("/api/biz/" + f.businessId() + "/professionals/" + f.professionalId() + "/time-off", f.ownerToken(), Map.of("from", from.toString(), "to", to.toString())).status()).isEqualTo(200);
        List<String> t = slotStarts(f, 4);
        assertThat(t).doesNotContain("14:15", "15:00", "16:45").contains("14:00", "17:00");
        // closure
        post("/api/biz/" + f.businessId() + "/closures", f.ownerToken(), Map.of("date", LocalDate.now(ALMATY).plusDays(5).toString(), "reason", "holiday"));
        assertThat(slotStarts(f, 5)).isEmpty();
        // past dates and lead time
        assertThat(slotStarts(f, -1)).isEmpty();
        assertThat(slotStarts(f, 0)).allSatisfy(x -> assertThat(LocalTime.parse(x)).isAfter(LocalTime.now(ALMATY).plusMinutes(59)));
        // beyond the advance limit
        assertThat(slotStarts(f, 61)).isEmpty();
    }

    @Test void bookingConflictsReturn409WithAlternatives() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c1 = register("CUSTOMER"), c2 = register("CUSTOMER");
        assertThat(book(c1, f, at(3, 15, 0)).status()).isEqualTo(201);
        Res clash = book(c2, f, at(3, 15, 30));
        assertThat(clash.status()).isEqualTo(409);
        assertThat(clash.body().get("code").asText()).isEqualTo("SLOT_UNAVAILABLE");
        assertThat(clash.body().get("details").get("alternatives").size()).isGreaterThan(0);
        // adjacent slot is fine (half-open interval)
        assertThat(book(c2, f, at(3, 16, 0)).status()).isEqualTo(201);
        // not on the grid / inside break / outside hours are refused as well
        assertThat(book(c2, f, at(3, 13, 0)).status()).isEqualTo(409);
        assertThat(book(c2, f, at(3, 22, 0)).status()).isEqualTo(409);
        assertThat(book(c2, f, at(3, 10, 7)).status()).isEqualTo(409);
    }

    @Test void concurrentRequestsForSameSlotProduceExactlyOneBooking() throws Exception {
        Fixture f = fixture(true, 24, 0);
        int n = 10;
        List<Long> customers = new ArrayList<>();
        for (int i = 0; i < n; i++) customers.add(userId(register("CUSTOMER")));
        Instant start = at(4, 11, 0);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), conflict = new AtomicInteger(), other = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (long cid : customers) fs.add(pool.submit(() -> {
            try {
                go.await();
                bookingService.create(cid, new BookingService.CreateRequest(f.serviceId(), f.professionalId(), start, null, null), null);
                ok.incrementAndGet();
            } catch (com.sulu.common.ApiException e) { if (e.code().equals("SLOT_UNAVAILABLE")) conflict.incrementAndGet(); else other.incrementAndGet(); }
            catch (Exception e) { other.incrementAndGet(); }
            return null;
        }));
        go.countDown();
        for (Future<?> fu : fs) fu.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(n - 1);
        assertThat(other.get()).isZero();
        assertThat(db.sql("SELECT count(*) FROM bookings WHERE professional_id=:p AND start_at=:s AND status IN ('PENDING','CONFIRMED')").param("p", f.professionalId()).param("s", java.sql.Timestamp.from(start)).query(Long.class).single()).isEqualTo(1);
    }

    @Test void databaseConstraintAloneStopsOverlapEvenIfApplicationCheckIsBypassed() throws Exception {
        Fixture f = fixture(true, 24, 0);
        long cid = userId(register("CUSTOMER"));
        insertBooking(cid, f, at(6, 10, 0), "CONFIRMED");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> insertBooking(cid, f, at(6, 10, 30), "PENDING"));
        insertBooking(cid, f, at(6, 10, 30), "CANCELLED"); // cancelled bookings do not block
    }

    @Test void idempotencyKeyPreventsDuplicateBookings() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long a = book(c, f, at(3, 12, 0), "Idempotency-Key", "abc-1").body().get("id").asLong();
        Res again = book(c, f, at(3, 12, 0), "Idempotency-Key", "abc-1");
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.body().get("id").asLong()).isEqualTo(a);
    }

    @Test void anyProfessionalIsAssignedToAFreeOne() throws Exception {
        Fixture f = fixture(true, 24, 0);
        long second = post("/api/biz/" + f.businessId() + "/professionals", f.ownerToken(), Map.of("fullName", "Pro Two", "serviceIds", List.of(f.serviceId()))).body().get("id").asLong();
        List<Map<String, Object>> ph = new ArrayList<>();
        for (int d = 1; d <= 7; d++) ph.add(Map.of("weekday", d, "startTime", "09:00", "endTime", "21:00"));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/biz/" + f.businessId() + "/professionals/" + second + "/hours"), f.ownerToken(), ph);
        String c1 = register("CUSTOMER"), c2 = register("CUSTOMER"), c3 = register("CUSTOMER");
        Map<String, Object> body = Map.of("serviceId", f.serviceId(), "startAt", at(3, 11, 0).toString());
        long p1 = post("/api/bookings", c1, body).body().get("professionalId").asLong();
        long p2 = post("/api/bookings", c2, body).body().get("professionalId").asLong();
        assertThat(p1).isNotEqualTo(p2);
        assertThat(post("/api/bookings", c3, body).status()).isEqualTo(409);
    }

    @Test void statusMachineRejectsInvalidTransitionsAndEarlyCompletion() throws Exception {
        Fixture f = fixture(false, 24, 0); // manual confirmation
        String c = register("CUSTOMER");
        Res created = book(c, f, at(3, 10, 0));
        long id = created.body().get("id").asLong();
        String base = "/api/biz/" + f.businessId() + "/bookings/" + id;
        assertThat(created.body().get("status").asText()).isEqualTo("PENDING");
        assertThat(post(base + "/complete", f.ownerToken(), null).status()).isEqualTo(409);   // PENDING -> COMPLETED not allowed
        assertThat(post(base + "/confirm", f.ownerToken(), null).status()).isEqualTo(200);
        assertThat(post(base + "/confirm", f.ownerToken(), null).status()).isEqualTo(409);    // already confirmed
        assertThat(post(base + "/complete", f.ownerToken(), null).status()).isEqualTo(422);   // appointment has not started
        assertThat(post(base + "/bogus", f.ownerToken(), null).status()).isEqualTo(400);
        assertThat(post("/api/bookings/" + id + "/complete", c, null).status()).isIn(404, 405, 403); // customers have no such endpoint
        assertThat(post(base + "/cancel", f.ownerToken(), Map.of("reason", "illness")).status()).isEqualTo(200);
        assertThat(post(base + "/confirm", f.ownerToken(), null).status()).isEqualTo(409);    // cancelled is terminal
        assertThat(db.sql("SELECT count(*) FROM booking_status_history WHERE booking_id=:i").param("i", id).query(Long.class).single()).isEqualTo(3);
    }

    @Test void completedAndNoShowOnlyAfterStartAndOnlyByBusiness() throws Exception {
        Fixture f = fixture(true, 24, 0);
        long cid = userId(register("CUSTOMER"));
        long done = insertBooking(cid, f, Instant.now().minusSeconds(7200), "CONFIRMED");
        long miss = insertBooking(cid, f, Instant.now().minusSeconds(14400), "CONFIRMED");
        assertThat(post("/api/biz/" + f.businessId() + "/bookings/" + done + "/complete", f.ownerToken(), null).status()).isEqualTo(200);
        assertThat(post("/api/biz/" + f.businessId() + "/bookings/" + miss + "/no-show", f.ownerToken(), null).status()).isEqualTo(200);
        assertThat(db.sql("SELECT status FROM bookings WHERE id=:i").param("i", done).query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(db.sql("SELECT status FROM bookings WHERE id=:i").param("i", miss).query(String.class).single()).isEqualTo("NO_SHOW");
    }

    @Test void customerCancellationRespectsPolicyWindow() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long far = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(get("/api/bookings/" + far, c).body().get("canCancel").asBoolean()).isTrue();
        assertThat(post("/api/bookings/" + far + "/cancel", c, Map.of("reason", "plans changed")).status()).isEqualTo(200);
        assertThat(get("/api/bookings?scope=cancelled", c).body().size()).isEqualTo(1);
        // within the 24h window (booked ~2h ahead by direct insert) the customer can no longer cancel
        long cid = userId(c);
        long near = insertBooking(cid, f, Instant.now().plusSeconds(7200), "CONFIRMED");
        Res late = post("/api/bookings/" + near + "/cancel", c, null);
        assertThat(late.status()).isEqualTo(422);
        assertThat(late.body().get("code").asText()).isEqualTo("CANCELLATION_WINDOW_PASSED");
        // the business can still cancel
        assertThat(post("/api/biz/" + f.businessId() + "/bookings/" + near + "/cancel", f.ownerToken(), Map.of("reason", "sorry")).status()).isEqualTo(200);
    }

    @Test void reschedulingMovesBookingAndFreesOldSlot() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER"), other = register("CUSTOMER");
        long id = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(book(other, f, at(3, 10, 0)).status()).isEqualTo(409);
        assertThat(post("/api/bookings/" + id + "/reschedule", c, Map.of("startAt", at(3, 10, 15).toString())).status()).isEqualTo(200); // overlaps itself: allowed
        assertThat(post("/api/bookings/" + id + "/reschedule", c, Map.of("startAt", at(3, 16, 0).toString())).status()).isEqualTo(200);
        assertThat(book(other, f, at(3, 10, 0)).status()).isEqualTo(201);                       // old slot is free again
        assertThat(post("/api/bookings/" + id + "/reschedule", c, Map.of("startAt", at(3, 10, 0).toString())).status()).isEqualTo(409); // now taken
        assertThat(Instant.parse(get("/api/bookings/" + id, c).body().get("startAt").asText())).isEqualTo(at(3, 16, 0));
    }

    @Test void promotionsValidateAndCannotBeRedeemedTwice() throws Exception {
        Fixture f = fixture(true, 24, 0);
        post("/api/biz/" + f.businessId() + "/promotions", f.ownerToken(), Map.of("code", "SAVE20" + f.businessId(), "percentOff", 20, "maxRedemptions", 5));
        String code = "SAVE20" + f.businessId();
        String c = register("CUSTOMER");
        Res first = post("/api/bookings", c, Map.of("serviceId", f.serviceId(), "professionalId", f.professionalId(), "startAt", at(3, 10, 0).toString(), "promoCode", code.toLowerCase()));
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().get("discountMinor").asLong()).isEqualTo(200000);
        assertThat(first.body().get("totalMinor").asLong()).isEqualTo(800000);
        Res second = post("/api/bookings", c, Map.of("serviceId", f.serviceId(), "professionalId", f.professionalId(), "startAt", at(3, 12, 0).toString(), "promoCode", code));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().get("code").asText()).isEqualTo("PROMO_ALREADY_USED");
        assertThat(post("/api/bookings", register("CUSTOMER"), Map.of("serviceId", f.serviceId(), "professionalId", f.professionalId(), "startAt", at(3, 14, 0).toString(), "promoCode", "NOPE")).status()).isEqualTo(422);
        // a promo of another business is not applicable
        Fixture g = fixture(true, 24, 0);
        assertThat(post("/api/bookings", register("CUSTOMER"), Map.of("serviceId", g.serviceId(), "professionalId", g.professionalId(), "startAt", at(3, 10, 0).toString(), "promoCode", code)).status()).isEqualTo(422);
    }

    @Test void searchFilterAndSortReturnRealResults() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String uniq = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        db.sql("UPDATE businesses SET name=:n, city='Шымкент' WHERE id=:i").param("n", uniq + " Salon").param("i", f.businessId()).update();
        Res r = get("/api/businesses?q=" + uniq, null);
        assertThat(r.body().get("total").asInt()).isEqualTo(1);
        assertThat(get("/api/businesses?q=" + uniq + "&city=" + java.net.URLEncoder.encode("Алматы", "UTF-8"), null).body().get("total").asInt()).isZero();
        assertThat(get("/api/search?q=" + uniq, null).body().get("businesses").size()).isEqualTo(1);
        assertThat(get("/api/services?q=haircut&businessId=" + f.businessId(), null).body().get("items").get(0).get("name").asText()).isEqualTo("Haircut");
        assertThat(get("/api/services?businessId=" + f.businessId() + "&minPriceMinor=2000000", null).body().get("total").asInt()).isZero();
        assertThat(get("/api/services?businessId=" + f.businessId() + "&maxPriceMinor=1000000&sort=price_asc", null).body().get("total").asInt()).isEqualTo(1);
        assertThat(get("/api/businesses?size=1000", null).body().get("size").asInt()).isEqualTo(50); // page size is capped
        assertThat(get("/api/search?q=%25_%5C", null).status()).isEqualTo(200); // wildcard characters are escaped
    }

    @Test void archivedServicesDisappearFromCatalogButKeepHistory() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long id = book(c, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(post("/api/biz/" + f.businessId() + "/services/" + f.serviceId() + "/active?value=false", f.ownerToken(), null).status()).isEqualTo(200);
        assertThat(get("/api/services/" + f.serviceId(), null).status()).isEqualTo(404);
        assertThat(book(register("CUSTOMER"), f, at(3, 12, 0)).status()).isEqualTo(404);
        assertThat(get("/api/bookings/" + id, c).body().get("serviceName").asText()).isEqualTo("Haircut");
    }
}
