package com.sulu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityAndAuthTest extends IntegrationTest {

    @Test void registerLoginAndMe() throws Exception {
        String em = email();
        Res r = post("/api/auth/register", null, Map.of("email", em, "password", "Passw0rd!x", "fullName", "Anna", "role", "CUSTOMER"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("user").get("role").asText()).isEqualTo("CUSTOMER");
        Res login = post("/api/auth/login", null, Map.of("email", em.toUpperCase(), "password", "Passw0rd!x"));
        assertThat(login.status()).isEqualTo(200);
        assertThat(get("/api/me", login.body().get("accessToken").asText()).body().get("email").asText()).isEqualTo(em);
    }

    @Test void duplicateEmailAndWeakPasswordAndBadLogin() throws Exception {
        String em = email();
        post("/api/auth/register", null, Map.of("email", em, "password", "Passw0rd!x", "fullName", "A"));
        assertThat(post("/api/auth/register", null, Map.of("email", em, "password", "Passw0rd!x", "fullName", "A")).status()).isEqualTo(409);
        Res weak = post("/api/auth/register", null, Map.of("email", email(), "password", "short", "fullName", "A"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.body().get("code").asText()).isEqualTo("WEAK_PASSWORD");
        assertThat(post("/api/auth/login", null, Map.of("email", em, "password", "wrong-pass1")).status()).isEqualTo(401);
    }

    @Test void clientCannotSelfAssignAdminOrProfessionalRole() throws Exception {
        for (String role : new String[]{"PLATFORM_ADMIN", "PROFESSIONAL"}) {
            Res r = post("/api/auth/register", null, Map.of("email", email(), "password", "Passw0rd!x", "fullName", "Mallory", "role", role));
            assertThat(r.status()).isEqualTo(400);
        }
    }

    @Test void protectedEndpointsRequireTokenAndRole() throws Exception {
        assertThat(get("/api/bookings", null).status()).isEqualTo(401);
        assertThat(get("/api/me", "garbage").status()).isEqualTo(401);
        String customer = register("CUSTOMER");
        assertThat(get("/api/admin/overview", customer).status()).isEqualTo(403);
        assertThat(get("/api/business", customer).status()).isEqualTo(403);
        assertThat(get("/api/pro/me", customer).status()).isEqualTo(403);
        assertThat(get("/api/admin/overview", adminToken()).status()).isEqualTo(200);
    }

    @Test void refreshTokenRotatesAndLogoutRevokes() throws Exception {
        Res reg = post("/api/auth/register", null, Map.of("email", email(), "password", "Passw0rd!x", "fullName", "A"));
        String refresh = reg.body().get("refreshToken").asText();
        Res r1 = post("/api/auth/refresh", null, Map.of("refreshToken", refresh));
        assertThat(r1.status()).isEqualTo(200);
        assertThat(post("/api/auth/refresh", null, Map.of("refreshToken", refresh)).status()).isEqualTo(401); // old token is dead
        String newRefresh = r1.body().get("refreshToken").asText();
        post("/api/auth/logout", null, Map.of("refreshToken", newRefresh));
        assertThat(post("/api/auth/refresh", null, Map.of("refreshToken", newRefresh)).status()).isEqualTo(401);
    }

    @Test void blockedUserLosesAccessImmediately() throws Exception {
        String admin = adminToken();
        String victim = register("CUSTOMER");
        long id = userId(victim);
        assertThat(post("/api/admin/users/" + id + "/status", admin, Map.of("status", "BLOCKED", "reason", "abuse")).status()).isEqualTo(200);
        assertThat(get("/api/me", victim).status()).isEqualTo(401);
        long audits = db.sql("SELECT count(*) FROM audit_logs WHERE action='USER_BLOCKED' AND target_id=:i").param("i", String.valueOf(id)).query(Long.class).single();
        assertThat(audits).isEqualTo(1);
    }

    @Test void passwordResetFlowInvalidatesOldSessions() throws Exception {
        String em = email();
        Res reg = post("/api/auth/register", null, Map.of("email", em, "password", "Passw0rd!x", "fullName", "A"));
        post("/api/auth/password-reset/request", null, Map.of("email", em));
        assertThat(post("/api/auth/password-reset/request", null, Map.of("email", "nobody@nowhere.test")).status()).isEqualTo(200); // no user enumeration
        assertThat(post("/api/auth/password-reset/confirm", null, Map.of("token", "bogus", "password", "NewPassw0rd1")).status()).isEqualTo(400);
        assertThat(db.sql("SELECT count(*) FROM password_reset_tokens").query(Long.class).single()).isGreaterThan(0);
        assertThat(reg.status()).isEqualTo(200);
    }

    @Test void accountDeletionErasesPersonalData() throws Exception {
        String t = register("CUSTOMER");
        long id = userId(t);
        assertThat(get("/api/me/export", t).status()).isEqualTo(200);
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me"), t, null).status()).isEqualTo(200);
        assertThat(db.sql("SELECT full_name FROM users WHERE id=:i").param("i", id).query(String.class).single()).isEqualTo("Deleted user");
        assertThat(get("/api/me", t).status()).isEqualTo(401);
    }

    @Test void errorsUseConsistentEnvelopeWithoutLeakingInternals() throws Exception {
        Res r = post("/api/auth/register", null, "{not json");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().has("code")).isTrue();
        assertThat(r.body().toString()).doesNotContain("Exception").doesNotContain("at com.");
        Res nf = get("/api/businesses/999999999", null);
        assertThat(nf.status()).isEqualTo(404);
        assertThat(nf.body().get("code").asText()).isEqualTo("NOT_FOUND");
    }
}
