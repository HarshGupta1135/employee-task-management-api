package com.harsh.taskmanager.service;

import com.harsh.taskmanager.dto.CreateTaskRequest;
import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.dto.TaskResponse;
import com.harsh.taskmanager.dto.UpdateTaskRequest;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.Task;
import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.exception.BadRequestException;
import com.harsh.taskmanager.exception.ConflictException;
import com.harsh.taskmanager.exception.ResourceNotFoundException;
import com.harsh.taskmanager.mapper.TaskMapper;
import com.harsh.taskmanager.repository.TaskRepository;
import com.harsh.taskmanager.repository.UserRepository;
import com.harsh.taskmanager.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private TaskService taskService;

    private User admin;
    private User alice;
    private User bob;
    private UserPrincipal adminPrincipal;
    private UserPrincipal alicePrincipal;
    private UserPrincipal bobPrincipal;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, userRepository, new TaskMapper(CLOCK), CLOCK);

        admin = user(1L, "Admin", Role.ADMIN);
        alice = user(2L, "Alice", Role.USER);
        bob = user(3L, "Bob", Role.USER);
        adminPrincipal = UserPrincipal.from(admin);
        alicePrincipal = UserPrincipal.from(alice);
        bobPrincipal = UserPrincipal.from(bob);
    }

    @Nested
    class GetTasks {

        @Test
        void returnsMappedPageAndAddsIdAsTieBreakerToTheSort() {
            Task task = task(10L, alice, alice);
            when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenAnswer(invocation -> new PageImpl<>(List.of(task), invocation.getArgument(1), 1));

            PageResponse<TaskResponse> page = taskService.getTasks(
                    TaskFilter.empty(), PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "priority")), alicePrincipal);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(taskRepository).findAll(any(Specification.class), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort())
                    .containsExactly(Sort.Order.desc("priority"), Sort.Order.asc("id"));

            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content()).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo(10L);
                assertThat(response.assignee().fullName()).isEqualTo("Alice");
            });
            assertThat(page.sort()).containsExactly("priority,desc", "id,asc");
        }

        @Test
        void rejectsUnknownSortProperty() {
            Pageable sortedByPassword = PageRequest.of(0, 10, Sort.by("password"));

            assertThatThrownBy(() -> taskService.getTasks(TaskFilter.empty(), sortedByPassword, adminPrincipal))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cannot sort by 'password'");
            verifyNoInteractions(taskRepository);
        }
    }

    @Nested
    class GetTask {

        @Test
        void throwsNotFoundWhenTaskDoesNotExist() {
            when(taskRepository.findWithUsersById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTask(99L, adminPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found with id 99");
        }

        @Test
        void deniesAccessToTaskOfAnotherEmployee() {
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task(10L, alice, admin)));

            assertThatThrownBy(() -> taskService.getTask(10L, bobPrincipal))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void allowsCreatorEvenWhenTaskIsAssignedToSomeoneElse() {
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task(10L, bob, alice)));

            TaskResponse response = taskService.getTask(10L, alicePrincipal);

            assertThat(response.createdBy().id()).isEqualTo(alice.getId());
        }

        @Test
        void allowsAdminToReadAnyTaskAndFlagsOverdueTasks() {
            Task task = task(10L, alice, alice);
            task.setDueDate(TODAY.minusDays(1));
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task));

            TaskResponse response = taskService.getTask(10L, adminPrincipal);

            assertThat(response.overdue()).isTrue();
        }
    }

    @Nested
    class CreateTask {

        @Test
        void userCreatesTaskAssignedToThemselvesWithDefaultStatus() {
            when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            CreateTaskRequest request = new CreateTaskRequest("  Write report  ", "   ", null, TaskPriority.HIGH, null, TODAY);

            TaskResponse response = taskService.createTask(request, alicePrincipal);

            assertThat(response.title()).isEqualTo("Write report");
            assertThat(response.description()).isNull();
            assertThat(response.status()).isEqualTo(TaskStatus.TODO);
            assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
            assertThat(response.assignee().id()).isEqualTo(alice.getId());
            assertThat(response.createdBy().id()).isEqualTo(alice.getId());
        }

        @Test
        void userCannotAssignTaskToAnotherEmployee() {
            when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
            CreateTaskRequest request = new CreateTaskRequest("Write report", null, null, TaskPriority.LOW, bob.getId(), null);

            assertThatThrownBy(() -> taskService.createTask(request, alicePrincipal))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Only administrators can assign tasks to other employees");
            verify(taskRepository, never()).save(any());
        }

        @Test
        void adminCanAssignAnyEmployee() {
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            CreateTaskRequest request = new CreateTaskRequest("Deploy", null, TaskStatus.IN_PROGRESS, TaskPriority.CRITICAL, bob.getId(), null);

            TaskResponse response = taskService.createTask(request, adminPrincipal);

            assertThat(response.assignee().id()).isEqualTo(bob.getId());
            assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        }

        @Test
        void adminAssigningUnknownEmployeeGetsNotFound() {
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(42L)).thenReturn(Optional.empty());
            CreateTaskRequest request = new CreateTaskRequest("Deploy", null, null, TaskPriority.LOW, 42L, null);

            assertThatThrownBy(() -> taskService.createTask(request, adminPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found with id 42");
        }
    }

    @Nested
    class UpdateTask {

        @Test
        void rejectsStaleVersion() {
            Task task = task(10L, alice, alice);
            task.setVersion(3L);
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.updateTask(10L, update(alice.getId(), null, 2L), alicePrincipal))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("current version: 3");
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void userCannotReassignTask() {
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task(10L, alice, alice)));

            assertThatThrownBy(() -> taskService.updateTask(10L, update(bob.getId(), null, null), alicePrincipal))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Only administrators can reassign tasks");
        }

        @Test
        void rejectsMovingDueDateIntoThePast() {
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task(10L, alice, alice)));

            assertThatThrownBy(() -> taskService.updateTask(10L, update(alice.getId(), TODAY.minusDays(1), null), alicePrincipal))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Due date cannot be in the past");
        }

        @Test
        void keepsAnExistingPastDueDateAndAppliesChanges() {
            Task task = task(10L, alice, alice);
            task.setDueDate(TODAY.minusDays(5));
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task));
            when(taskRepository.saveAndFlush(task)).thenReturn(task);

            TaskResponse response = taskService.updateTask(10L, update(alice.getId(), TODAY.minusDays(5), 0L), alicePrincipal);

            assertThat(response.title()).isEqualTo("Updated title");
            assertThat(response.status()).isEqualTo(TaskStatus.DONE);
            assertThat(response.overdue()).isFalse();
        }

        @Test
        void adminCanUnassignTask() {
            when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task(10L, alice, alice)));
            when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskResponse response = taskService.updateTask(10L, update(null, null, null), adminPrincipal);

            assertThat(response.assignee()).isNull();
        }
    }

    @Test
    void assigneeCanChangeStatus() {
        Task task = task(10L, alice, admin);
        when(taskRepository.findWithUsersById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(10L, TaskStatus.IN_PROGRESS, alicePrincipal);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void deleteThrowsNotFoundForMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void deleteRemovesExistingTask() {
        Task task = task(10L, alice, admin);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskService.deleteTask(10L);

        verify(taskRepository).delete(task);
    }

    private static UpdateTaskRequest update(Long assigneeId, LocalDate dueDate, Long version) {
        return new UpdateTaskRequest("Updated title", "Updated description", TaskStatus.DONE, TaskPriority.MEDIUM,
                assigneeId, dueDate, version);
    }

    private static User user(Long id, String name, Role role) {
        User user = new User(name, name.toLowerCase() + "@example.com", "hash", role);
        user.setId(id);
        return user;
    }

    private static Task task(Long id, User assignee, User createdBy) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Task " + id);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setAssignee(assignee);
        task.setCreatedBy(createdBy);
        task.setVersion(0L);
        return task;
    }
}
