package com.sulu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantIsolationTest extends IntegrationTest {

    @Test void ownerCannotTouchAnotherBusiness() throws Exception {
        Fixture a = fixture(true, 24, 0), b = fixture(true, 24, 0);
        String base = "/api/biz/" + a.businessId();
        assertThat(get(base, b.ownerToken()).status()).isEqualTo(404);
        assertThat(get(base + "/services", b.ownerToken()).status()).isEqualTo(404);
        assertThat(get(base + "/professionals", b.ownerToken()).status()).isEqualTo(404);
        assertThat(get(base + "/customers", b.ownerToken()).status()).isEqualTo(404);
        assertThat(get(base + "/analytics", b.ownerToken()).status()).isEqualTo(404);
        assertThat(post(base + "/services/" + a.serviceId() + "/active?value=false", b.ownerToken(), null).status()).isEqualTo(404);
        // own business with someone else's service id is also refused
        assertThat(put("/api/biz/" + b.businessId() + "/services/" + a.serviceId(), b.ownerToken(),
                Map.of("categoryId", a.categoryId(), "name", "Hacked", "priceMinor", 1, "durationMin", 30)).status()).isEqualTo(404);
        assertThat(db.sql("SELECT name FROM services WHERE id=:i").param("i", a.serviceId()).query(String.class).single()).isEqualTo("Haircut");
    }

    @Test void ownerCannotAssignForeignProfessionalToOwnService() throws Exception {
        Fixture a = fixture(true, 24, 0), b = fixture(true, 24, 0);
        var r = put("/api/biz/" + b.businessId() + "/services/" + b.serviceId(), b.ownerToken(),
                Map.of("categoryId", b.categoryId(), "name", "Haircut", "priceMinor", 100, "durationMin", 30, "professionalIds", java.util.List.of(a.professionalId())));
        assertThat(r.status()).isEqualTo(404);
    }

    @Test void customersOnlySeeTheirOwnBookings() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c1 = register("CUSTOMER"), c2 = register("CUSTOMER");
        long id = book(c1, f, at(3, 10, 0)).body().get("id").asLong();
        assertThat(get("/api/bookings/" + id, c1).status()).isEqualTo(200);
        assertThat(get("/api/bookings/" + id, c2).status()).isEqualTo(404);
        assertThat(post("/api/bookings/" + id + "/cancel", c2, null).status()).isEqualTo(404);
        assertThat(get("/api/bookings?scope=upcoming", c2).body().size()).isZero();
    }

    @Test void businessViewHidesNothingItNeedsButCustomerViewHidesCustomerPii() throws Exception {
        Fixture f = fixture(true, 24, 0);
        String c = register("CUSTOMER");
        long id = book(c, f, at(3, 11, 0)).body().get("id").asLong();
        assertThat(get("/api/bookings/" + id, c).body().has("customerPhone")).isFalse();
        assertThat(get("/api/biz/" + f.businessId() + "/bookings/" + id, f.ownerToken()).body().get("customerName").asText()).startsWith("Test");
    }

    @Test void pendingBusinessIsNotPubliclyVisibleUntilApproved() throws Exception {
        String owner = register("BUSINESS_OWNER");
        long bid = post("/api/business", owner, Map.of("name", "Hidden Salon", "city", "Алматы", "address", "x")).body().get("id").asLong();
        assertThat(get("/api/businesses/" + bid, null).status()).isEqualTo(404);
        String admin = adminToken();
        assertThat(post("/api/admin/businesses/" + bid + "/status", admin, Map.of("status", "APPROVED")).status()).isEqualTo(200);
        assertThat(get("/api/businesses/" + bid, null).status()).isEqualTo(200);
        assertThat(db.sql("SELECT count(*) FROM audit_logs WHERE action='BUSINESS_APPROVED' AND target_id=:i").param("i", String.valueOf(bid)).query(Long.class).single()).isEqualTo(1);
    }
}
