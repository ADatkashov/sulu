package com.sulu.payment;

import com.sulu.common.SuluProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** DEVELOPMENT ONLY. Moves no money. Enabled solely via SULU_PAYMENTS_SIMULATED=true (on in the 'dev' profile). */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {
    private final boolean enabled;
    public SimulatedPaymentProvider(SuluProperties props) { this.enabled = props.payments().simulatedEnabled(); }
    public String name() { return "SIMULATED"; }
    public boolean simulated() { return true; }
    public boolean enabled() { return enabled; }
    public String createCharge(long amountMinor, String currency, String bookingReference) { return "SIM-" + UUID.randomUUID(); }
    public void refund(String providerRef, long amountMinor) { /* nothing to do: no real funds */ }
}
