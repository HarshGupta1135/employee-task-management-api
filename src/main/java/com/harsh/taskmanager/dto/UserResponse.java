package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
