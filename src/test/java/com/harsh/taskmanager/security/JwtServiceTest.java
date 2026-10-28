package com.harsh.taskmanager.security;

import com.harsh.taskmanager.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("a-test-secret-that-is-definitely-longer-than-256-bits".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UserPrincipal ALICE = new UserPrincipal(2L, "Alice", "alice@example.com", "hash", Role.USER);

    @Test
    void generatedTokenCanBeVerifiedAndCarriesTheEmailAsSubject() {
        JwtService jwtService = service(SECRET, "task-manager", NOW);

        String token = jwtService.generateToken(ALICE);

        assertThat(jwtService.extractSubject(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, "task-manager", NOW).generateToken(ALICE);
        JwtService twoHoursLater = service(SECRET, "task-manager", NOW.plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> twoHoursLater.extractSubject(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenWithTamperedPayloadIsRejected() {
        JwtService jwtService = service(SECRET, "task-manager", NOW);
        String[] parts = jwtService.generateToken(ALICE).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin@example.com\",\"iss\":\"task-manager\"}".getBytes(StandardCharsets.UTF_8));

        String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractSubject(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("another-secret-that-is-also-longer-than-256-bits!!".getBytes(StandardCharsets.UTF_8));
        String token = service(otherSecret, "task-manager", NOW).generateToken(ALICE);

        assertThatThrownBy(() -> service(SECRET, "task-manager", NOW).extractSubject(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = service(SECRET, "someone-else", NOW).generateToken(ALICE);

        assertThatThrownBy(() -> service(SECRET, "task-manager", NOW).extractSubject(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan256BitsIsRefusedAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service(shortSecret, "task-manager", NOW)).isInstanceOf(WeakKeyException.class);
    }

    private static JwtService service(String secret, String issuer, Instant now) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(1), issuer), Clock.fixed(now, ZoneOffset.UTC));
    }
}
