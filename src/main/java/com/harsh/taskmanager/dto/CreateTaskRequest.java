package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @Schema(example = "Prepare quarterly report")
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
        String title,

        @Schema(example = "Collect numbers from finance and summarise them in the Q3 template")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Schema(description = "Defaults to TODO when omitted")
        TaskStatus status,

        @NotNull(message = "Priority is required")
        TaskPriority priority,

        @Schema(description = "Employee to assign. Admins may assign anyone; users can only assign themselves. Defaults to the caller for users.")
        @Positive(message = "Assignee id must be a positive number")
        Long assigneeId,

        @Schema(example = "2030-01-31")
        @FutureOrPresent(message = "Due date cannot be in the past")
        LocalDate dueDate
) {
}
