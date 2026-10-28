package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "Status is required")
        TaskStatus status
) {
}
