package com.harsh.taskmanager.repository;

import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.entity.Task;
import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Composable JPA criteria used to build the task list query from optional filters.
 */
public final class TaskSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private TaskSpecifications() {
    }

    /**
     * Combines the requested filters. When {@code visibleToUserId} is not null the result is restricted
     * to tasks that user created or is assigned to (used for non-admin callers).
     */
    public static Specification<Task> build(TaskFilter filter, Long visibleToUserId) {
        List<Specification<Task>> specifications = new ArrayList<>();
        if (visibleToUserId != null) {
            specifications.add(visibleTo(visibleToUserId));
        }
        if (filter.status() != null) {
            specifications.add(hasStatus(filter.status()));
        }
        if (filter.priority() != null) {
            specifications.add(hasPriority(filter.priority()));
        }
        if (filter.assigneeId() != null) {
            specifications.add(isAssignedTo(filter.assigneeId()));
        }
        if (filter.search() != null) {
            specifications.add(matchesText(filter.search()));
        }
        return Specification.allOf(specifications);
    }

    static Specification<Task> visibleTo(Long userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("assignee").get("id"), userId),
                cb.equal(root.get("createdBy").get("id"), userId));
    }

    static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    static Specification<Task> isAssignedTo(Long assigneeId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    /** Case-insensitive "contains" match on title or description. LIKE wildcards in the input are escaped. */
    static Specification<Task> matchesText(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
