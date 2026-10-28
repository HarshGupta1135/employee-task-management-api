package com.harsh.taskmanager.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.repository.TaskRepository;
import com.harsh.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the application against real PostgreSQL and Redis containers (the same setup as docker-compose):
 * verifies the Flyway schema on PostgreSQL, Hibernate validation, and Redis caching with eviction.
 * Skipped automatically when Docker is not available.
 */
// @Testcontainers is declared first so its extension stops the containers only after Spring has closed the
// (dirtied) context; otherwise the Redis client would log reconnect attempts against a stopped container.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "postgres", "redis"})
@DirtiesContext
class PostgresRedisIntegrationTest {

    private static final String PASSWORD = "Password123";
    private static final String CACHE_KEY_PATTERN = "taskList::*";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String adminToken;
    private Long aliceId;

    @BeforeEach
    void setUp() throws Exception {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> staleKeys = redisTemplate.keys(CACHE_KEY_PATTERN);
        if (!staleKeys.isEmpty()) {
            redisTemplate.delete(staleKeys);
        }

        userRepository.save(new User("Admin", "admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        aliceId = userRepository.save(new User("Alice", "alice@example.com", passwordEncoder.encode(PASSWORD), Role.USER)).getId();
        adminToken = login();
    }

    @Test
    void taskListIsCachedInRedisAsJsonAndEvictedOnWrite() throws Exception {
        long taskId = createTask("Cached task");

        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Cached task"));

        Set<String> keys = redisTemplate.keys(CACHE_KEY_PATTERN);
        assertThat(keys).hasSize(1);
        String cachedJson = redisTemplate.opsForValue().get(keys.iterator().next());
        assertThat(cachedJson).contains("\"title\":\"Cached task\"").contains("\"fullName\":\"Alice\"");

        // Change the row behind the cache's back: a cache hit must still return the cached (old) title,
        // which proves the second request was served from Redis and deserialised correctly.
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setTitle("Changed directly in the database");
            taskRepository.save(task);
        });
        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Cached task"))
                .andExpect(jsonPath("$.content[0].assignee.fullName").value("Alice"))
                .andExpect(jsonPath("$.content[0].dueDate").value(LocalDate.now().plusDays(3).toString()));

        // Any write through the API evicts the cached pages.
        createTask("Second task");
        assertThat(redisTemplate.keys(CACHE_KEY_PATTERN)).isEmpty();

        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Changed directly in the database"));
    }

    private long createTask(String title) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title, "priority", "HIGH", "assigneeId", aliceId, "dueDate", LocalDate.now().plusDays(3).toString()));
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "admin@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
