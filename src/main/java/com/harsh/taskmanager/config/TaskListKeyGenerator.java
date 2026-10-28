package com.harsh.taskmanager.config;

import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.security.UserPrincipal;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;

/**
 * Builds the cache key for the task list from the caller's visibility scope, the filters and the page request.
 *
 * <p>Admins all see the same data, so they share one scope; a regular user only sees their own tasks,
 * so their entries are scoped by user id. Example key:
 * {@code user:7|status=TODO|priority=*|assignee=*|search=*|page=0|size=10|sort=dueDate: ASC}
 */
public class TaskListKeyGenerator implements KeyGenerator {

    private static final String ANY = "*";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        TaskFilter filter = TaskFilter.empty();
        Pageable pageable = Pageable.unpaged();
        UserPrincipal user = null;
        for (Object param : params) {
            if (param instanceof TaskFilter taskFilter) {
                filter = taskFilter;
            } else if (param instanceof Pageable page) {
                pageable = page;
            } else if (param instanceof UserPrincipal principal) {
                user = principal;
            }
        }
        if (user == null) {
            throw new IllegalStateException("Task list cache key requires the current user");
        }

        String scope = user.isAdmin() ? "admin" : "user:" + user.getId();
        return scope
                + "|status=" + valueOrAny(filter.status())
                + "|priority=" + valueOrAny(filter.priority())
                + "|assignee=" + valueOrAny(filter.assigneeId())
                + "|search=" + valueOrAny(filter.search())
                + "|page=" + (pageable.isPaged() ? pageable.getPageNumber() : ANY)
                + "|size=" + (pageable.isPaged() ? pageable.getPageSize() : ANY)
                + "|sort=" + pageable.getSort();
    }

    private static String valueOrAny(Object value) {
        return value == null ? ANY : value.toString();
    }
}
