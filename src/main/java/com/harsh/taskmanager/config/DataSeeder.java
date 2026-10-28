package com.harsh.taskmanager.config;

import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.Task;
import com.harsh.taskmanager.entity.TaskPriority;
import com.harsh.taskmanager.entity.TaskStatus;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.repository.TaskRepository;
import com.harsh.taskmanager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Creates an admin, two employees and a handful of tasks the first time the application starts
 * against an empty database, so the API can be explored immediately. Disable with {@code APP_SEED_ENABLED=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;
    private final Clock clock;

    public DataSeeder(UserRepository userRepository, TaskRepository taskRepository, PasswordEncoder passwordEncoder,
                      SeedProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Users already exist, skipping demo data");
            return;
        }

        User admin = createUser("System Admin", properties.adminEmail(), properties.adminPassword(), Role.ADMIN);
        User alice = createUser("Alice Johnson", "alice@taskmanager.com", properties.userPassword(), Role.USER);
        User bob = createUser("Bob Smith", "bob@taskmanager.com", properties.userPassword(), Role.USER);

        LocalDate today = LocalDate.now(clock);
        taskRepository.saveAll(List.of(
                task("Set up CI pipeline", "Configure GitHub Actions to build and test every pull request",
                        TaskStatus.IN_PROGRESS, TaskPriority.HIGH, alice, admin, today.plusDays(3)),
                task("Write onboarding guide", "Document the local setup steps for new joiners",
                        TaskStatus.TODO, TaskPriority.MEDIUM, alice, admin, today.plusDays(7)),
                task("Update API documentation", "Describe the new filter parameters in Swagger",
                        TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, alice, alice, today.plusDays(5)),
                task("Prepare sprint demo", "Collect screenshots and talking points for the Friday demo",
                        TaskStatus.TODO, TaskPriority.CRITICAL, bob, admin, today.plusDays(1)),
                task("Renew staging SSL certificate", "The staging certificate has expired and must be renewed",
                        TaskStatus.TODO, TaskPriority.HIGH, bob, admin, today.minusDays(1)),
                task("Fix login form validation", "Show the field-level errors returned by the API",
                        TaskStatus.DONE, TaskPriority.HIGH, bob, bob, today.minusDays(2)),
                task("Clean up old feature flags", null,
                        TaskStatus.TODO, TaskPriority.LOW, bob, bob, today.plusDays(10)),
                task("Review database indexes", "Check slow queries on the tasks table",
                        TaskStatus.TODO, TaskPriority.LOW, null, admin, today.plusDays(14))));

        log.info("Demo data created: admin {} and employees {}, {} (passwords are configured under app.seed)",
                admin.getEmail(), alice.getEmail(), bob.getEmail());
    }

    private User createUser(String fullName, String email, String rawPassword, Role role) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.save(new User(fullName, normalisedEmail, passwordEncoder.encode(rawPassword), role));
    }

    private static Task task(String title, String description, TaskStatus status, TaskPriority priority,
                             User assignee, User createdBy, LocalDate dueDate) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setPriority(priority);
        task.setAssignee(assignee);
        task.setCreatedBy(createdBy);
        task.setDueDate(dueDate);
        return task;
    }
}
