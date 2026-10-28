package com.harsh.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.taskmanager.dto.CreateTaskRequest;
import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.dto.TaskResponse;
import com.harsh.taskmanager.dto.UserSummary;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.exception.ResourceNotFoundException;
import com.harsh.taskmanager.security.CustomUserDetailsService;
import com.harsh.taskmanager.security.JwtService;
import com.harsh.taskmanager.security.RestAccessDeniedHandler;
import com.harsh.taskmanager.security.RestAuthenticationEntryPoint;
import com.harsh.taskmanager.security.SecurityConfig;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test: real MVC configuration, real security rules and real exception handling,
 * with the service layer mocked.
 */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TaskControllerTest {

    private static final UserPrincipal ALICE = new UserPrincipal(2L, "Alice", "alice@example.com", "hash", Role.USER);
    private static final UserPrincipal ADMIN = new UserPrincipal(1L, "Admin", "admin@example.com", "hash", Role.ADMIN);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    // Required by SecurityConfig; requests in this test authenticate through spring-security-test instead of JWTs.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void createTaskReturns201WithLocationHeader() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest("Write report", "Q3 numbers", null, TaskPriority.HIGH, null,
                LocalDate.now().plusDays(3));
        when(taskService.createTask(any(CreateTaskRequest.class), eq(ALICE))).thenReturn(sampleTask(7L));

        mockMvc.perform(post("/api/tasks")
                        .with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tasks/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.assignee.fullName").value("Alice"));
    }

    @Test
    void createTaskWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        String body = """
                {"title": "", "description": "x", "priority": null, "dueDate": "2000-01-01"}
                """;

        mockMvc.perform(post("/api/tasks")
                        .with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'title')].message", hasSize(2)))
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'priority')].message").value("Priority is required"))
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'dueDate')].message").value("Due date cannot be in the past"));

        verifyNoInteractions(taskService);
    }

    @Test
    void unknownEnumValueReturns400ListingAllowedValues() throws Exception {
        mockMvc.perform(patch("/api/tasks/7/status")
                        .with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"FINISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("status"))
                .andExpect(jsonPath("$.validationErrors[0].message").value(containsString("TODO, IN_PROGRESS, DONE")));
    }

    @Test
    void requestWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));

        verifyNoInteractions(taskService);
    }

    @Test
    void listPassesFiltersPaginationAndSortingToTheService() throws Exception {
        PageResponse<TaskResponse> page = new PageResponse<>(List.of(sampleTask(7L)), 1, 5, 6, 2, false, true,
                List.of("priority,desc"));
        when(taskService.getTasks(any(TaskFilter.class), any(Pageable.class), eq(ALICE))).thenReturn(page);

        mockMvc.perform(get("/api/tasks")
                        .with(user(ALICE))
                        .param("status", "TODO")
                        .param("search", "report")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "priority,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.page").value(1));

        ArgumentCaptor<TaskFilter> filterCaptor = ArgumentCaptor.forClass(TaskFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskService).getTasks(filterCaptor.capture(), pageableCaptor.capture(), eq(ALICE));
        assertThat(filterCaptor.getValue()).isEqualTo(new TaskFilter(TaskStatus.TODO, null, null, "report"));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort()).containsExactly(Sort.Order.desc("priority"));
    }

    @Test
    void listUsesNewestFirstByDefault() throws Exception {
        when(taskService.getTasks(any(TaskFilter.class), any(Pageable.class), eq(ALICE)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, List.of()));

        mockMvc.perform(get("/api/tasks").with(user(ALICE))).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskService).getTasks(any(TaskFilter.class), pageableCaptor.capture(), eq(ALICE));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"));
    }

    @Test
    void missingTaskReturns404() throws Exception {
        when(taskService.getTask(99L, ALICE)).thenThrow(new ResourceNotFoundException("Task", 99L));

        mockMvc.perform(get("/api/tasks/99").with(user(ALICE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 99"))
                .andExpect(jsonPath("$.path").value("/api/tasks/99"));
    }

    @Test
    void taskOfAnotherEmployeeReturns403() throws Exception {
        when(taskService.getTask(5L, ALICE)).thenThrow(new AccessDeniedException("You do not have permission to access this task"));

        mockMvc.perform(get("/api/tasks/5").with(user(ALICE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this task"));
    }

    @Test
    void deleteByRegularUserReturns403() throws Exception {
        mockMvc.perform(delete("/api/tasks/7").with(user(ALICE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        verifyNoInteractions(taskService);
    }

    @Test
    void deleteByAdminReturns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/7").with(user(ADMIN)))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(7L);
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/tasks/abc").with(user(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("id"));
    }

    private static TaskResponse sampleTask(Long id) {
        UserSummary alice = new UserSummary(2L, "Alice", "alice@example.com");
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new TaskResponse(id, "Write report", "Q3 numbers", TaskStatus.TODO, TaskPriority.HIGH,
                LocalDate.of(2030, 1, 1), false, alice, alice, 0L, now, now);
    }
}
