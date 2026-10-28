package com.harsh.taskmanager.controller;

import com.harsh.taskmanager.dto.CreateTaskRequest;
import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.dto.TaskResponse;
import com.harsh.taskmanager.dto.UpdateTaskRequest;
import com.harsh.taskmanager.dto.UpdateTaskStatusRequest;
import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.exception.ApiError;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Create, read, update and delete tasks")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired token", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List tasks with filters, pagination and sorting",
            description = "Admins see every task; users see tasks they created or are assigned to. "
                    + "Pages are zero-based (page=0), size is capped at 100. "
                    + "Sort with sort=property,asc|desc using id, title, status, priority, dueDate, createdAt or updatedAt.")
    @ApiResponse(responseCode = "200", description = "A page of tasks")
    @ApiResponse(responseCode = "400", description = "Invalid filter or sort property", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<TaskResponse> getTasks(
            @Parameter(description = "Filter by status") @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Filter by priority") @RequestParam(required = false) TaskPriority priority,
            @Parameter(description = "Filter by assignee id") @RequestParam(required = false) Long assigneeId,
            @Parameter(description = "Case-insensitive text search in title and description")
            @RequestParam(required = false) @Size(max = 100, message = "Search text must be at most 100 characters") String search,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return taskService.getTasks(new TaskFilter(status, priority, assigneeId, search), pageable, currentUser);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    @ApiResponse(responseCode = "200", description = "The task")
    @ApiResponse(responseCode = "403", description = "Task belongs to another employee", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TaskResponse getTask(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return taskService.getTask(id, currentUser);
    }

    @PostMapping
    @Operation(summary = "Create a task", description = "Users can only create tasks for themselves; admins can assign any employee.")
    @ApiResponse(responseCode = "201", description = "Task created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "User tried to assign the task to someone else", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Assignee not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        TaskResponse created = taskService.createTask(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a task", description = "Only admins may change the assignee. Send the last read version to guard against lost updates.")
    @ApiResponse(responseCode = "200", description = "Task updated")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Not allowed to update this task or change its assignee", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Task or assignee not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Version conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TaskResponse updateTask(@PathVariable Long id,
                                   @Valid @RequestBody UpdateTaskRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return taskService.updateTask(id, request, currentUser);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change only the status of a task")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Not allowed to update this task", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TaskResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateTaskStatusRequest request,
                                     @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return taskService.updateStatus(id, request.status(), currentUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (ADMIN only)")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }
}
