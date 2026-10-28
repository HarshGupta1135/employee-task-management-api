package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
