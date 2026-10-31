package com.sulu.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sulu.common.ApiException;
import com.sulu.common.SuluProperties;
import com.sulu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService svc;
    private final SuluProperties props;
    private final ObjectMapper mapper;
    public PaymentController(PaymentService svc, SuluProperties props, ObjectMapper mapper) { this.svc = svc; this.props = props; this.mapper = mapper; }

    public record PayReq(@NotBlank String method) {}
    public record SimReq(boolean success) {}

    @PostMapping("/api/bookings/{id}/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> pay(@PathVariable long id, @Valid @RequestBody PayReq r, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return svc.start(CurrentUser.require().id(), id, r.method(), key);
    }

    @GetMapping("/api/bookings/{id}/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<Map<String, Object>> list(@PathVariable long id) { return svc.forBooking(id, CurrentUser.require().id()); }

    /** DEV ONLY (404-equivalent unless simulated payments are enabled). */
    @PostMapping("/api/payments/{id}/simulate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Object> simulate(@PathVariable long id, @RequestBody SimReq r) { return svc.simulate(CurrentUser.require().id(), id, r.success()); }

    /** Provider webhook: HMAC-SHA256 of the raw body in X-Sulu-Signature. Duplicate event ids are acknowledged without effect. */
    @PostMapping("/api/payments/webhook/{provider}")
    public Map<String, Object> webhook(@PathVariable String provider, @RequestHeader(value = "X-Sulu-Signature", required = false) String signature, @RequestBody String raw) throws Exception {
        String secret = props.payments().webhookSecret();
        if (secret == null || secret.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Webhook secret is not configured");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE", "Invalid signature");
        JsonNode n = mapper.readTree(raw);
        if (!n.hasNonNull("eventId") || !n.hasNonNull("type") || !n.hasNonNull("providerRef")) throw ApiException.bad("BAD_PAYLOAD", "eventId, type and providerRef are required");
        boolean applied = svc.handleEvent(provider.toUpperCase(), n.get("eventId").asText(), n.get("type").asText(), n.get("providerRef").asText(), raw);
        return Map.of("received", true, "duplicate", !applied);
    }
}
