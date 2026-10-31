package com.sulu.payment;

import com.sulu.common.ApiException;
import com.sulu.engagement.AuditService;
import com.sulu.engagement.NotificationService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Set<String> METHODS = Set.of("CARD", "APPLE_PAY", "GOOGLE_PAY", "LOCAL");

    private final JdbcClient db;
    private final Map<String, PaymentProvider> providers = new HashMap<>();
    private final NotificationService notifications;
    private final AuditService audit;

    public PaymentService(JdbcClient db, List<PaymentProvider> list, NotificationService notifications, AuditService audit) {
        this.db = db; this.notifications = notifications; this.audit = audit;
        list.forEach(p -> providers.put(p.name(), p));
    }

    /** The provider used for new online payments (first enabled one). */
    public Optional<PaymentProvider> activeProvider() { return providers.values().stream().filter(PaymentProvider::enabled).findFirst(); }

    /** Starts an online payment for a booking owned by {@code customerId}. Payment is optional: unpaid bookings stay valid. */
    @Transactional
    public Map<String, Object> start(long customerId, long bookingId, String method, String idemKey) {
        if (!METHODS.contains(method)) throw ApiException.bad("BAD_METHOD", "Unsupported payment method");
        PaymentProvider provider = activeProvider().orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED", "Online payments are not configured"));
        var bk = db.sql("SELECT id, reference, status, price_minor, discount_minor, currency, payment_status FROM bookings WHERE id=:i AND customer_id=:c FOR UPDATE")
                .param("i", bookingId).param("c", customerId).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Booking"));
        if (idemKey != null && !idemKey.isBlank()) {
            var ex = db.sql("SELECT id FROM payments WHERE booking_id=:b AND idempotency_key=:k").param("b", bookingId).param("k", idemKey).query(Long.class).optional();
            if (ex.isPresent()) return get(ex.get());
        } else idemKey = null;
        if (!Set.of("PENDING", "CONFIRMED").contains(bk.get("status"))) throw ApiException.conflict("BOOKING_NOT_PAYABLE", "Booking is not active");
        if ("PAID".equals(bk.get("payment_status"))) throw ApiException.conflict("ALREADY_PAID", "Booking is already paid");
        long amount = ((Number) bk.get("price_minor")).longValue() - ((Number) bk.get("discount_minor")).longValue();
        if (amount <= 0) throw ApiException.unprocessable("NOTHING_TO_PAY", "Nothing to pay");
        // supersede earlier unfinished attempts
        db.sql("UPDATE payments SET status='CANCELLED', updated_at=now() WHERE booking_id=:b AND status IN ('INITIATED','PENDING')").param("b", bookingId).update();
        String currency = ((String) bk.get("currency")).trim();
        String ref = provider.createCharge(amount, currency, (String) bk.get("reference"));
        long id = db.sql("""
            INSERT INTO payments(booking_id, provider, provider_ref, method, amount_minor, currency, status, simulated, idempotency_key)
            VALUES (:b,:p,:r,:m,:a,:c,'PENDING',:s,:k) RETURNING id""")
            .param("b", bookingId).param("p", provider.name()).param("r", ref).param("m", method).param("a", amount).param("c", currency)
            .param("s", provider.simulated()).param("k", idemKey).query(Long.class).single();
        syncBooking(bookingId);
        return get(id);
    }

    public Map<String, Object> get(long paymentId) {
        var p = db.sql("SELECT id, booking_id, provider, method, amount_minor, refunded_minor, currency, status, simulated, created_at FROM payments WHERE id=:i").param("i", paymentId).query().listOfRows();
        if (p.isEmpty()) throw ApiException.notFound("Payment");
        return com.sulu.common.Rows.row(p.get(0));
    }

    public List<Map<String, Object>> forBooking(long bookingId, long customerId) {
        return com.sulu.common.Rows.rows(db.sql("""
            SELECT p.id, p.booking_id, p.provider, p.method, p.amount_minor, p.refunded_minor, p.currency, p.status, p.simulated, p.created_at
            FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.booking_id=:b AND b.customer_id=:c ORDER BY p.id""").param("b", bookingId).param("c", customerId).query().listOfRows());
    }

    /** DEV ONLY: lets the UI resolve a simulated payment through exactly the same event path a real webhook uses. */
    @Transactional
    public Map<String, Object> simulate(long customerId, long paymentId, boolean success) {
        var p = db.sql("SELECT p.provider, p.provider_ref, p.simulated FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.id=:i AND b.customer_id=:c")
                .param("i", paymentId).param("c", customerId).query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Payment"));
        if (!(Boolean) p.get("simulated") || activeProvider().filter(PaymentProvider::simulated).isEmpty())
            throw ApiException.forbidden("Simulation is only available for simulated payments in development");
        handleEvent((String) p.get("provider"), UUID.randomUUID().toString(), success ? "payment.succeeded" : "payment.failed", (String) p.get("provider_ref"), "simulated");
        return get(paymentId);
    }

    /** Applies a provider event. Idempotent: the (provider,event_id) unique key makes duplicate deliveries no-ops. Returns false for duplicates. */
    @Transactional
    public boolean handleEvent(String provider, String eventId, String type, String providerRef, String payload) {
        var pay = db.sql("SELECT id, booking_id, status, amount_minor FROM payments WHERE provider=:p AND provider_ref=:r FOR UPDATE").param("p", provider).param("r", providerRef)
                .query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("Payment"));
        long payId = ((Number) pay.get("id")).longValue(), bookingId = ((Number) pay.get("booking_id")).longValue();
        try {
            db.sql("INSERT INTO payment_events(payment_id, provider, event_id, type, payload) VALUES (:pi,:p,:e,:t,:pl)")
              .param("pi", payId).param("p", provider).param("e", eventId).param("t", type).param("pl", payload == null ? null : payload.substring(0, Math.min(payload.length(), 2000))).update();
        } catch (DuplicateKeyException dup) { return false; }
        String status = (String) pay.get("status");
        boolean open = status.equals("INITIATED") || status.equals("PENDING");
        String next = switch (type) {
            case "payment.succeeded" -> open ? "SUCCEEDED" : null;
            case "payment.failed" -> open ? "FAILED" : null;
            case "payment.cancelled" -> open ? "CANCELLED" : null;
            default -> null;
        };
        if (next == null) return true;
        db.sql("UPDATE payments SET status=:s, updated_at=now() WHERE id=:i").param("s", next).param("i", payId).update();
        syncBooking(bookingId);
        var bk = db.sql("SELECT customer_id, status FROM bookings WHERE id=:i").param("i", bookingId).query().listOfRows().get(0);
        long customerId = ((Number) bk.get("customer_id")).longValue();
        if (next.equals("SUCCEEDED")) {
            notifications.notify(customerId, "PAYMENT_SUCCEEDED", "Оплата прошла", "", "/bookings/" + bookingId, "pay:" + payId + ":ok");
            if (Set.of("CANCELLED", "REJECTED").contains(bk.get("status"))) refundForBooking(bookingId, null); // paid after the booking was cancelled
        } else if (next.equals("FAILED")) {
            notifications.notify(customerId, "PAYMENT_FAILED", "Оплата не прошла", "Попробуйте ещё раз или оплатите в салоне", "/bookings/" + bookingId, "pay:" + payId + ":fail");
        }
        return true;
    }

    /** Full refund of everything paid for a booking; cancels unfinished attempts. Never blocks the caller on provider failure. */
    @Transactional
    public void refundForBooking(long bookingId, Long actorId) {
        db.sql("UPDATE payments SET status='CANCELLED', updated_at=now() WHERE booking_id=:b AND status IN ('INITIATED','PENDING')").param("b", bookingId).update();
        for (var p : db.sql("SELECT id FROM payments WHERE booking_id=:b AND status IN ('SUCCEEDED','PARTIALLY_REFUNDED')").param("b", bookingId).query(Long.class).list()) {
            try { refund(p, null, actorId); }
            catch (Exception e) {
                log.warn("Refund for payment {} failed: {}", p, e.getMessage());
                audit.log(actorId, "REFUND_FAILED", "PAYMENT", p, e.getMessage());
            }
        }
        syncBooking(bookingId);
    }

    /** amountMinor == null → refund the remaining balance. */
    @Transactional
    public Map<String, Object> refund(long paymentId, Long amountMinor, Long actorId) {
        var p = db.sql("SELECT id, booking_id, provider, provider_ref, amount_minor, refunded_minor, status FROM payments WHERE id=:i FOR UPDATE").param("i", paymentId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Payment"));
        String st = (String) p.get("status");
        if (!st.equals("SUCCEEDED") && !st.equals("PARTIALLY_REFUNDED")) throw ApiException.conflict("NOT_REFUNDABLE", "Payment is not refundable");
        long total = ((Number) p.get("amount_minor")).longValue(), done = ((Number) p.get("refunded_minor")).longValue();
        long amount = amountMinor == null ? total - done : amountMinor;
        if (amount <= 0 || amount > total - done) throw ApiException.bad("BAD_AMOUNT", "Invalid refund amount");
        PaymentProvider provider = providers.get((String) p.get("provider"));
        if (provider == null || !provider.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED", "Payment provider unavailable");
        provider.refund((String) p.get("provider_ref"), amount);
        long newDone = done + amount;
        db.sql("UPDATE payments SET refunded_minor=:r, status=:s, updated_at=now() WHERE id=:i").param("r", newDone).param("s", newDone == total ? "REFUNDED" : "PARTIALLY_REFUNDED").param("i", paymentId).update();
        long bookingId = ((Number) p.get("booking_id")).longValue();
        syncBooking(bookingId);
        audit.log(actorId, "REFUND", "PAYMENT", paymentId, "amountMinor=" + amount);
        long customerId = db.sql("SELECT customer_id FROM bookings WHERE id=:i").param("i", bookingId).query(Long.class).single();
        notifications.notify(customerId, "PAYMENT_REFUNDED", "Возврат средств оформлен", "", "/bookings/" + bookingId, "refund:" + paymentId + ":" + newDone);
        return get(paymentId);
    }

    /** Keeps bookings.payment_status consistent with the latest meaningful payment. */
    void syncBooking(long bookingId) {
        var pays = db.sql("SELECT status FROM payments WHERE booking_id=:b ORDER BY id DESC").param("b", bookingId).query(String.class).list();
        String status = "UNPAID";
        for (String s : pays) {
            if (s.equals("SUCCEEDED")) { status = "PAID"; break; }
            if (s.equals("REFUNDED")) { status = "REFUNDED"; break; }
            if (s.equals("PARTIALLY_REFUNDED")) { status = "PARTIALLY_REFUNDED"; break; }
            if (s.equals("INITIATED") || s.equals("PENDING")) { status = "PENDING"; break; }
            if (s.equals("FAILED")) { status = "FAILED"; break; }
        }
        db.sql("UPDATE bookings SET payment_status=:s, updated_at=now() WHERE id=:b").param("s", status).param("b", bookingId).update();
    }
}
