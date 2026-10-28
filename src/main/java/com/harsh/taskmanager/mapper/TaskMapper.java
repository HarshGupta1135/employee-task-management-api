package com.harsh.taskmanager.mapper;

import com.harsh.taskmanager.dto.TaskResponse;
import com.harsh.taskmanager.dto.UserSummary;
import com.harsh.taskmanager.entity.Task;
import com.harsh.taskmanager.entity.TaskStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class TaskMapper {

    private final Clock clock;

    public TaskMapper(Clock clock) {
        this.clock = clock;
    }

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                isOverdue(task),
                UserSummary.from(task.getAssignee()),
                UserSummary.from(task.getCreatedBy()),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && task.getStatus() != TaskStatus.DONE
                && task.getDueDate().isBefore(LocalDate.now(clock));
    }
}
