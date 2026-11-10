package com.king.esb.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/** Issues and validates HS256 JWTs. The secret is supplied via {@code esb.security.jwt-secret}. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(SecurityProperties properties) {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "esb.security.jwt-secret must be configured with at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = properties.getTokenTtlMinutes();
    }

    public String generate(String username, List<Role> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Parses and verifies a token, returning its claims; throws on invalid/expired tokens. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public long ttlSeconds() {
        return ttlMinutes * 60;
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        Object roles = claims.get("roles");
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }
}
