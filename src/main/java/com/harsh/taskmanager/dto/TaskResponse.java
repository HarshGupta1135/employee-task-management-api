package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean overdue,
        UserSummary assignee,
        UserSummary createdBy,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
