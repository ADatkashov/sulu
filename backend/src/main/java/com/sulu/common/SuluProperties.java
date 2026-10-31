package com.sulu.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sulu")
public record SuluProperties(Jwt jwt, String corsOrigins, Storage storage, Payments payments, Admin admin,
                             boolean seedDemo, RateLimit rateLimit) {
    public record Jwt(String secret, int accessTtlMinutes, int refreshTtlDays) {}
    public record Storage(String dir, long maxBytes) {}
    public record Payments(String webhookSecret, boolean simulatedEnabled) {}
    public record Admin(String email, String password) {}
    public record RateLimit(int authPerMinute) {}
}
