package com.harsh.taskmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo data created on first start-up when the database has no users.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(
        boolean enabled,
        String adminEmail,
        String adminPassword,
        String userPassword
) {
}
