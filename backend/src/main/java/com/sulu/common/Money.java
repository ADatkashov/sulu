package com.sulu.common;

/** Helpers for integer-minor-unit arithmetic (no floating point anywhere in money math). */
public final class Money {
    private Money() {}

    /** percent in basis points (1/100 of a percent), rounded half-up. */
    public static long bps(long amountMinor, int bps) { return (amountMinor * bps + 5_000) / 10_000; }
}
