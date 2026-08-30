package com.featureflagplatform.auth.security;

import com.featureflagplatform.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Stateless JWT issuance/validation. Access tokens only — no refresh tokens,
 * no server-side session store. That's the deliberate trade-off for a
 * project this size: simpler to build, simpler to explain, and fully
 * horizontally scalable (any backend instance can validate any token with
 * no shared state), at the cost of not being able to revoke a single token
 * before it expires. See .claude/decisions/ADR-005-jwt-auth.md.
 */
@Component
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_DISPLAY_NAME = "displayName";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(CLAIM_DISPLAY_NAME, user.getDisplayName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }

    /** Returns the parsed claims if the token is well-formed, signed correctly, and unexpired. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
