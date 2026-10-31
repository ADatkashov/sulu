package com.sulu.payment;

/** Provider seam. Real gateways (cards, Apple/Google Pay, local KZ/TR providers) implement this; the domain never sees provider specifics. */
public interface PaymentProvider {
    String name();
    /** true for development simulations — payments created through it are flagged and labelled in every response. */
    boolean simulated();
    boolean enabled();
    /** Creates a charge at the provider and returns its reference. The final result arrives asynchronously via webhook. */
    String createCharge(long amountMinor, String currency, String bookingReference);
    void refund(String providerRef, long amountMinor);
}
