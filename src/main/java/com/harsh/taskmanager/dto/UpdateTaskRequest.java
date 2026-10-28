package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full replacement of a task (PUT semantics). A null {@code assigneeId} means "unassigned".
 */
public record UpdateTaskRequest(
        @Schema(example = "Prepare quarterly report")
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "Status is required")
        TaskStatus status,

        @NotNull(message = "Priority is required")
        TaskPriority priority,

        @Positive(message = "Assignee id must be a positive number")
        Long assigneeId,

        @Schema(description = "Must not be moved into the past. An existing past due date may be kept unchanged.")
        LocalDate dueDate,

        @Schema(description = "Optional optimistic-locking guard: the version you last read. A mismatch returns 409 Conflict.")
        @PositiveOrZero(message = "Version must not be negative")
        Long version
) {
}
