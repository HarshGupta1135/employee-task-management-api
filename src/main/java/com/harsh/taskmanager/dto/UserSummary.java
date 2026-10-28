package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.User;

/** Compact user representation embedded in task responses. */
public record UserSummary(
        Long id,
        String fullName,
        String email
) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
