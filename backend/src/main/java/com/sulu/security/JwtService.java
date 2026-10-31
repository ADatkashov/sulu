package com.sulu.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sulu.common.SuluProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SuluProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JwtService(SuluProperties props, Clock clock) {
        this.props = props; this.clock = clock;
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("SULU_JWT_SECRET must be set (>= 32 bytes). Use the 'dev' profile for local development.");
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public JwtDecoder decoder() { return decoder; }

    public String accessToken(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("sulu").subject(Long.toString(userId))
                .issuedAt(now).expiresAt(now.plus(props.jwt().accessTtlMinutes(), ChronoUnit.MINUTES)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public int accessTtlSeconds() { return props.jwt().accessTtlMinutes() * 60; }

    /** Opaque random token (refresh / reset / invite); only its SHA-256 is stored. */
    public String newOpaqueToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
