package com.harsh.taskmanager.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.taskmanager.config.CacheConfig;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.repository.TaskRepository;
import com.harsh.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the full stack: HTTP layer, JWT security, services, JPA and the database
 * (H2 migrated by Flyway). Every request authenticates with a real token obtained from /api/auth/login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskManagementIntegrationTest {

    private static final String PASSWORD = "Password123";

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
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User alice;
    private User bob;
    private String adminToken;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        taskListCache().clear();

        userRepository.save(new User("Admin", "admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        alice = userRepository.save(new User("Alice", "alice@example.com", passwordEncoder.encode(PASSWORD), Role.USER));
        bob = userRepository.save(new User("Bob", "bob@example.com", passwordEncoder.encode(PASSWORD), Role.USER));

        adminToken = login("admin@example.com");
        aliceToken = login("alice@example.com");
        bobToken = login("bob@example.com");
    }

    @Test
    void taskLifecycleFromCreationToDeletion() throws Exception {
        MvcResult created = mockMvc.perform(authorized(post("/api/tasks"), aliceToken)
                        .content(json(Map.of("title", "Prepare demo", "priority", "HIGH", "dueDate", future(5)))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/tasks/")))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assignee.email").value("alice@example.com"))
                .andExpect(jsonPath("$.createdBy.email").value("alice@example.com"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        long taskId = readJson(created).get("id").asLong();

        mockMvc.perform(authorized(get("/api/tasks/" + taskId), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare demo"));

        Map<String, Object> update = Map.of("title", "Prepare sprint demo", "description", "Slides and live demo",
                "status", "IN_PROGRESS", "priority", "CRITICAL", "assigneeId", alice.getId(), "dueDate", future(6), "version", 0);
        mockMvc.perform(authorized(put("/api/tasks/" + taskId), aliceToken).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare sprint demo"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.version").value(1));

        // Re-sending the old version is rejected instead of silently overwriting the newer data.
        mockMvc.perform(authorized(put("/api/tasks/" + taskId), aliceToken).content(json(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(authorized(patch("/api/tasks/" + taskId + "/status"), aliceToken).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(authorized(delete("/api/tasks/" + taskId), adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/tasks/" + taskId), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found with id " + taskId));
    }

    @Test
    void securityErrorsUseConsistentStatusCodesAndBodies() throws Exception {
        long aliceTaskId = createTask(aliceToken, "Alice's task", "LOW", null);

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));

        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token is invalid"));

        mockMvc.perform(authorized(get("/api/tasks/" + aliceTaskId), bobToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this task"));

        mockMvc.perform(authorized(delete("/api/tasks/" + aliceTaskId), aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/tasks/" + aliceTaskId));

        mockMvc.perform(authorized(get("/api/users"), aliceToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(post("/api/tasks"), aliceToken)
                        .content(json(Map.of("title", "For Bob", "priority", "LOW", "assigneeId", bob.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only administrators can assign tasks to other employees"));

        mockMvc.perform(authorized(get("/api/tasks/987654"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidRequestsReturnStructuredValidationErrors() throws Exception {
        mockMvc.perform(authorized(post("/api/tasks"), aliceToken)
                        .content(json(Map.of("title", "ab", "priority", "LOW", "dueDate", "2001-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors", hasSize(2)))
                .andExpect(jsonPath("$.validationErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.validationErrors[1].field").value("title"));

        mockMvc.perform(authorized(get("/api/tasks"), adminToken).param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Cannot sort by 'password'")));

        mockMvc.perform(authorized(post("/api/auth/register"), null)
                        .content(json(Map.of("fullName", "X", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'password')]").exists());
    }

    @Test
    void listSupportsPaginationAndSorting() throws Exception {
        createTask(adminToken, "Bravo", "LOW", alice.getId());
        createTask(adminToken, "Delta", "CRITICAL", alice.getId());
        createTask(adminToken, "Alpha", "MEDIUM", bob.getId());
        createTask(adminToken, "Echo", "HIGH", bob.getId());
        createTask(adminToken, "Charlie", "HIGH", null);

        mockMvc.perform(authorized(get("/api/tasks"), adminToken)
                        .param("page", "0").param("size", "2").param("sort", "priority,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Priority sorts by business order (CRITICAL > HIGH > MEDIUM > LOW); ties are broken by id.
                .andExpect(jsonPath("$.content[0].title").value("Delta"))
                .andExpect(jsonPath("$.content[1].title").value("Echo"));

        mockMvc.perform(authorized(get("/api/tasks"), adminToken)
                        .param("page", "2").param("size", "2").param("sort", "priority,desc"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Bravo"))
                .andExpect(jsonPath("$.last").value(true));

        MvcResult byTitle = mockMvc.perform(authorized(get("/api/tasks"), adminToken).param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(titles(byTitle)).containsExactly("Alpha", "Bravo", "Charlie", "Delta", "Echo");
    }

    @Test
    void usersOnlySeeTheirOwnTasksAndCanFilter() throws Exception {
        createTask(adminToken, "Alice report", "HIGH", alice.getId());
        long aliceSecond = createTask(adminToken, "Alice review", "LOW", alice.getId());
        createTask(adminToken, "Bob report", "HIGH", bob.getId());
        mockMvc.perform(authorized(patch("/api/tasks/" + aliceSecond + "/status"), aliceToken)
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());

        assertThat(titles(list(aliceToken, Map.of()))).containsExactlyInAnyOrder("Alice report", "Alice review");
        assertThat(titles(list(aliceToken, Map.of("status", "DONE")))).containsExactly("Alice review");
        assertThat(titles(list(adminToken, Map.of("search", "REPORT")))).containsExactlyInAnyOrder("Alice report", "Bob report");
        assertThat(titles(list(adminToken, Map.of("assigneeId", bob.getId().toString())))).containsExactly("Bob report");
        assertThat(titles(list(adminToken, Map.of("priority", "LOW")))).containsExactly("Alice review");
    }

    @Test
    void creatorStillSeesTaskAfterAdminUnassignsIt() throws Exception {
        long taskId = createTask(aliceToken, "Created by Alice", "MEDIUM", null);

        Map<String, Object> unassign = new HashMap<>(Map.of("title", "Created by Alice", "status", "TODO", "priority", "MEDIUM"));
        unassign.put("assigneeId", null);
        mockMvc.perform(authorized(put("/api/tasks/" + taskId), adminToken).content(json(unassign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());

        assertThat(titles(list(aliceToken, Map.of()))).containsExactly("Created by Alice");
        assertThat(titles(list(bobToken, Map.of()))).isEmpty();
    }

    @Test
    void taskListIsCachedAndEvictedOnWrites() throws Exception {
        createTask(adminToken, "First", "LOW", alice.getId());
        taskListCache().clear();

        assertThat(titles(list(aliceToken, Map.of()))).containsExactly("First");
        assertThat(cachedEntries()).isEqualTo(1);

        createTask(aliceToken, "Second", "HIGH", null);
        assertThat(cachedEntries()).as("cache evicted after create").isZero();
        assertThat(titles(list(aliceToken, Map.of()))).containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    void listingTasksAvoidsTheNPlusOneProblem() throws Exception {
        for (int i = 0; i < 6; i++) {
            createTask(adminToken, "Task " + i, "MEDIUM", i % 2 == 0 ? alice.getId() : bob.getId());
        }
        taskListCache().clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(authorized(get("/api/tasks"), adminToken)).andExpect(jsonPath("$.content", hasSize(6)));

        // 1 query to load the authenticated user + 1 page query (tasks joined with users) + 1 count query.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        MvcResult registered = mockMvc.perform(authorized(post("/api/auth/register"), null)
                        .content(json(Map.of("fullName", "Priya Sharma", "email", "Priya@Example.com", "password", "Secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn();
        String token = readJson(registered).get("accessToken").asText();

        mockMvc.perform(authorized(get("/api/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("priya@example.com"));

        mockMvc.perform(authorized(post("/api/auth/register"), null)
                        .content(json(Map.of("fullName", "Priya Again", "email", "priya@example.com", "password", "Secret123"))))
                .andExpect(status().isConflict());

        mockMvc.perform(authorized(post("/api/auth/login"), null)
                        .content(json(Map.of("email", "priya@example.com", "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void adminCanPromoteUsersButNotChangeOwnRole() throws Exception {
        mockMvc.perform(authorized(patch("/api/users/" + bob.getId() + "/role"), adminToken).content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // The promotion takes effect immediately because the user is reloaded on every request.
        mockMvc.perform(authorized(get("/api/users"), bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        Long adminId = userRepository.findByEmail("admin@example.com").orElseThrow().getId();
        mockMvc.perform(authorized(patch("/api/users/" + adminId + "/role"), adminToken).content(json(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot change your own role"));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(authorized(post("/api/auth/login"), null)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private long createTask(String token, String title, String priority, Long assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "priority", priority, "dueDate", future(10)));
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId);
        }
        MvcResult result = mockMvc.perform(authorized(post("/api/tasks"), token).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private MvcResult list(String token, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = authorized(get("/api/tasks"), token).param("size", "50");
        params.forEach(request::param);
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private List<String> titles(MvcResult result) throws Exception {
        List<String> titles = new ArrayList<>();
        readJson(result).get("content").forEach(task -> titles.add(task.get("title").asText()));
        return titles;
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Cache taskListCache() {
        return cacheManager.getCache(CacheConfig.TASK_LIST_CACHE);
    }

    private int cachedEntries() {
        return ((ConcurrentMapCache) taskListCache()).getNativeCache().size();
    }

    private static String future(int days) {
        return LocalDate.now().plusDays(days).toString();
    }
}
