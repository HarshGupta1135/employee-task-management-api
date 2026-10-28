package com.harsh.taskmanager.service;

import com.harsh.taskmanager.config.CacheConfig;
import com.harsh.taskmanager.dto.CreateTaskRequest;
import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.TaskFilter;
import com.harsh.taskmanager.dto.TaskResponse;
import com.harsh.taskmanager.dto.UpdateTaskRequest;
import com.harsh.taskmanager.entity.Task;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.exception.BadRequestException;
import com.harsh.taskmanager.exception.ConflictException;
import com.harsh.taskmanager.exception.ResourceNotFoundException;
import com.harsh.taskmanager.mapper.TaskMapper;
import com.harsh.taskmanager.repository.TaskRepository;
import com.harsh.taskmanager.repository.TaskSpecifications;
import com.harsh.taskmanager.repository.UserRepository;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.util.SortValidator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Task business logic and access rules:
 * <ul>
 *     <li>ADMIN can see, assign, update and delete every task.</li>
 *     <li>USER can see and update only tasks they created or are assigned to, can only assign tasks to themselves,
 *     and cannot reassign or delete tasks.</li>
 * </ul>
 * Every write evicts the cached task lists so readers never see stale pages.
 */
@Service
public class TaskService {

    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "title", "status", "priority", "dueDate", "createdAt", "updatedAt");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, TaskMapper taskMapper, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.TASK_LIST_CACHE, keyGenerator = CacheConfig.TASK_LIST_KEY_GENERATOR)
    public PageResponse<TaskResponse> getTasks(TaskFilter filter, Pageable pageable, UserPrincipal currentUser) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE_PROPERTIES);
        Long visibleToUserId = currentUser.isAdmin() ? null : currentUser.getId();
        Page<Task> page = taskRepository.findAll(TaskSpecifications.build(filter, visibleToUserId), withStableOrder(pageable));
        return PageResponse.from(page, taskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id, UserPrincipal currentUser) {
        Task task = findTask(id);
        assertCanAccess(task, currentUser);
        return taskMapper.toResponse(task);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST_CACHE, allEntries = true)
    public TaskResponse createTask(CreateTaskRequest request, UserPrincipal currentUser) {
        User creator = findUser(currentUser.getId());

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(normaliseDescription(request.description()));
        task.setStatus(request.status() != null ? request.status() : TaskStatus.TODO);
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setCreatedBy(creator);
        task.setAssignee(resolveAssigneeForCreate(request.assigneeId(), currentUser, creator));

        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST_CACHE, allEntries = true)
    public TaskResponse updateTask(Long id, UpdateTaskRequest request, UserPrincipal currentUser) {
        Task task = findTask(id);
        assertCanAccess(task, currentUser);

        if (request.version() != null && !request.version().equals(task.getVersion())) {
            throw new ConflictException("Task " + id + " was modified by someone else (your version: " + request.version()
                    + ", current version: " + task.getVersion() + "). Reload it and try again.");
        }
        boolean dueDateChanged = !Objects.equals(request.dueDate(), task.getDueDate());
        if (dueDateChanged && request.dueDate() != null && request.dueDate().isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("Due date cannot be in the past");
        }

        task.setAssignee(resolveAssigneeForUpdate(task, request.assigneeId(), currentUser));
        task.setTitle(request.title().trim());
        task.setDescription(normaliseDescription(request.description()));
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());

        // Flush so the response carries the incremented version and the new updatedAt timestamp.
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST_CACHE, allEntries = true)
    public TaskResponse updateStatus(Long id, TaskStatus status, UserPrincipal currentUser) {
        Task task = findTask(id);
        assertCanAccess(task, currentUser);
        task.setStatus(status);
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    /** Only reachable by admins: enforced by the URL rule in SecurityConfig and {@code @PreAuthorize} on the controller. */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST_CACHE, allEntries = true)
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
        taskRepository.delete(task);
    }

    private Task findTask(Long id) {
        return taskRepository.findWithUsersById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static void assertCanAccess(Task task, UserPrincipal user) {
        if (!user.isAdmin() && !task.isAssignedTo(user.getId()) && !task.isCreatedBy(user.getId())) {
            throw new AccessDeniedException("You do not have permission to access this task");
        }
    }

    private User resolveAssigneeForCreate(Long assigneeId, UserPrincipal currentUser, User creator) {
        if (!currentUser.isAdmin()) {
            if (assigneeId != null && !assigneeId.equals(currentUser.getId())) {
                throw new AccessDeniedException("Only administrators can assign tasks to other employees");
            }
            return creator;
        }
        return assigneeId == null ? null : findUser(assigneeId);
    }

    private User resolveAssigneeForUpdate(Task task, Long assigneeId, UserPrincipal currentUser) {
        Long currentAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        if (Objects.equals(currentAssigneeId, assigneeId)) {
            return task.getAssignee();
        }
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only administrators can reassign tasks");
        }
        return assigneeId == null ? null : findUser(assigneeId);
    }

    private static String normaliseDescription(String description) {
        return (description == null || description.isBlank()) ? null : description.trim();
    }

    /** Adds the id as a tie-breaker so rows with equal sort values never move between pages. */
    private static Pageable withStableOrder(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
    }
}
