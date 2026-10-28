package com.harsh.taskmanager.config;

import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class TaskListKeyGeneratorTest {

    private final TaskListKeyGenerator keyGenerator = new TaskListKeyGenerator();

    private final UserPrincipal admin1 = new UserPrincipal(1L, "Admin One", "a1@example.com", "x", Role.ADMIN);
    private final UserPrincipal admin2 = new UserPrincipal(9L, "Admin Two", "a2@example.com", "x", Role.ADMIN);
    private final UserPrincipal alice = new UserPrincipal(2L, "Alice", "alice@example.com", "x", Role.USER);
    private final UserPrincipal bob = new UserPrincipal(3L, "Bob", "bob@example.com", "x", Role.USER);

    private final PageRequest page = PageRequest.of(0, 10, Sort.by("dueDate"));

    @Test
    void keyDescribesScopeFiltersAndPage() {
        Object key = generate(new TaskFilter(TaskStatus.TODO, null, null, "report"), page, alice);

        assertThat(key).isEqualTo("user:2|status=TODO|priority=*|assignee=*|search=report|page=0|size=10|sort=dueDate: ASC");
    }

    @Test
    void adminsShareEntriesButUsersAreIsolated() {
        TaskFilter filter = TaskFilter.empty();

        assertThat(generate(filter, page, admin1)).isEqualTo(generate(filter, page, admin2));
        assertThat(generate(filter, page, alice)).isNotEqualTo(generate(filter, page, bob));
    }

    @Test
    void differentPagesProduceDifferentKeys() {
        TaskFilter filter = TaskFilter.empty();

        assertThat(generate(filter, page, alice)).isNotEqualTo(generate(filter, page.next(), alice));
    }

    private Object generate(TaskFilter filter, PageRequest pageRequest, UserPrincipal user) {
        return keyGenerator.generate(new Object(), null, filter, pageRequest, user);
    }
}
