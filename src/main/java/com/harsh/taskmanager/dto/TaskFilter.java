package com.harsh.taskmanager.dto;

import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;

/**
 * Optional filters for the task list endpoint. Every field may be null, meaning "do not filter".
 */
public record TaskFilter(
        TaskStatus status,
        TaskPriority priority,
        Long assigneeId,
        String search
) {

    public TaskFilter {
        search = (search == null || search.isBlank()) ? null : search.trim();
    }

    public static TaskFilter empty() {
        return new TaskFilter(null, null, null, null);
    }
}
