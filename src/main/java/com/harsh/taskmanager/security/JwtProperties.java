package com.harsh.taskmanager.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret     Base64-encoded HMAC key, at least 256 bits once decoded
 * @param expiration lifetime of an access token
 * @param issuer     value written to and required in the {@code iss} claim
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer
) {
}
