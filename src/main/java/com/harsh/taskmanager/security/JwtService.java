package com.harsh.taskmanager.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HMAC-SHA signed JWT access tokens.
 */
@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "uid";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        // Throws WeakKeyException at startup if the configured secret is shorter than 256 bits.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.clock = clock;
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(properties.issuer())
                .claim(USER_ID_CLAIM, principal.getId())
                .claim(ROLE_CLAIM, principal.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies the signature, issuer and expiry of the token and returns its subject (the user's email).
     *
     * @throws JwtException             if the token is malformed, tampered with, expired or from another issuer
     * @throws IllegalArgumentException if the token is blank
     */
    public String extractSubject(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getExpirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
