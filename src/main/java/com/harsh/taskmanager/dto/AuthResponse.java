package com.harsh.taskmanager.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
