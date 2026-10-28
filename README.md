# Employee Task Management API

[![CI](https://github.com/HarshGupta1135/employee-task-management-api/actions/workflows/ci.yml/badge.svg)](https://github.com/HarshGupta1135/employee-task-management-api/actions/workflows/ci.yml)

A Spring Boot REST API that lets employees manage tasks behind JWT authentication with role-based access control (`ADMIN` / `USER`). It ships with a small web UI, Swagger documentation, Redis caching, Docker Compose and a test suite that covers unit, web-layer and integration tests (including real PostgreSQL and Redis through Testcontainers).

**Stack:** Java 17 · Spring Boot 3.5 · Spring Security 6 · Spring Data JPA / Hibernate · Flyway · H2 / PostgreSQL · Redis · JJWT · SpringDoc OpenAPI · JUnit 5 · Mockito · Testcontainers · Docker

---

## Contents

- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Assignment checklist](#assignment-checklist)
- [Frontend](#frontend)
- [API reference](#api-reference)
- [Access rules](#access-rules)
- [Pagination, sorting and filtering](#pagination-sorting-and-filtering)
- [Error responses](#error-responses)
- [Database](#database)
- [Caching](#caching)
- [Security design](#security-design)
- [Testing](#testing)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [Design decisions](#design-decisions)

---

## Quick start

Pick **one** of the options below. Each one starts the backend **and** the frontend, because the web UI is served by the backend itself.

### Option 1: Run locally with zero setup (only Java 17+ needed)

Uses an in-memory H2 database and an in-memory cache. No database installation required.

```bash
# macOS / Linux / Git Bash
./mvnw spring-boot:run

# Windows (cmd / PowerShell)
mvnw.cmd spring-boot:run
```

The Maven Wrapper downloads Maven automatically on first use.

| What | URL |
|---|---|
| Web UI (frontend) | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| H2 console | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:taskdb`, user `sa`, empty password) |
| Health check | http://localhost:8080/actuator/health |

> Port 8080 already in use? Run with `SERVER_PORT=9090 ./mvnw spring-boot:run` (PowerShell: `$env:SERVER_PORT=9090; .\mvnw.cmd spring-boot:run`).

### Option 2: Full stack with Docker Compose (PostgreSQL + Redis)

```bash
docker compose up --build
```

This builds the application image (multi-stage Dockerfile) and starts three containers: the app, PostgreSQL 16 and Redis 7. Flyway creates the schema and demo data is seeded on first start. Open http://localhost:8080.

- Different host port: `APP_PORT=9090 docker compose up --build`
- PostgreSQL is published on host port **5433** (user `taskuser`, password `taskpass`, database `taskdb`).
- Stop everything: `docker compose down` (add `-v` to also delete the database volume).

### Option 3: Local PostgreSQL without Docker

```bash
createdb -U postgres taskdb          # or create it in pgAdmin

# macOS / Linux / Git Bash
SPRING_PROFILES_ACTIVE=postgres \
DB_URL=jdbc:postgresql://localhost:5432/taskdb DB_USERNAME=postgres DB_PASSWORD=yourpassword \
./mvnw spring-boot:run
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
$env:DB_URL="jdbc:postgresql://localhost:5432/taskdb"
$env:DB_USERNAME="postgres"; $env:DB_PASSWORD="yourpassword"
.\mvnw.cmd spring-boot:run
```

The schema is created automatically by Flyway. Add the `redis` profile (`SPRING_PROFILES_ACTIVE=postgres,redis`) if a Redis server is running on `localhost:6379`.

---

## Demo accounts

Created automatically on the first start against an empty database (disable with `APP_SEED_ENABLED=false`):

| Role | Email | Password | Sees |
|---|---|---|---|
| ADMIN | `admin@taskmanager.com` | `Admin@123` | all 8 demo tasks, employee management |
| USER | `alice@taskmanager.com` | `User@1234` | her 3 tasks |
| USER | `bob@taskmanager.com` | `User@1234` | his 4 tasks |

New accounts can be registered from the UI or with `POST /api/auth/register` (always role `USER`; an admin can promote them).

---

## Assignment checklist

| Requirement | Implementation |
|---|---|
| Layered REST API (Controller, Service, Repository) | `controller` → `service` → `repository` packages; DTOs keep entities out of the web layer |
| Task fields: title, description, status, priority, assignee, due date | `Task` entity, plus creator, timestamps and an optimistic-locking `version` |
| JWT authentication + role-based access (ADMIN, USER) | Stateless Spring Security with a custom JWT filter, URL rules **and** `@PreAuthorize` method security, plus ownership checks in the service layer |
| Consistent 401 / 403 / 404 responses | One JSON `ApiError` format for every error, including those raised inside the security filter chain |
| Spring Data JPA + Hibernate with H2 or PostgreSQL, schema initialisation | Flyway migration `V1__init_schema.sql` (runs on H2 and PostgreSQL); Hibernate runs with `ddl-auto=validate` |
| Bean Validation with clear, structured errors | Constraints on every request DTO and query parameter; field-level `validationErrors` in responses |
| Pagination and sorting with `Pageable` | `GET /api/tasks?page=0&size=10&sort=priority,desc` plus filters (status, priority, assignee, text search) |
| Unit tests for a service and a controller (JUnit 5 + Mockito) | `TaskServiceTest`, `AuthServiceTest`, `JwtServiceTest`, `TaskControllerTest` (`@WebMvcTest`) and more |
| **Bonus:** Redis caching with eviction on updates | Task list pages cached in Redis (JSON, 10 min TTL), all pages evicted on every create / update / delete |
| **Bonus:** Multi-stage Dockerfile + Docker Compose | `Dockerfile` (Maven build stage → layered JRE runtime, non-root, health check), `docker-compose.yml` (app + PostgreSQL + Redis) |
| **Bonus:** SpringDoc OpenAPI / Swagger UI | `/swagger-ui.html` with JWT "Authorize" support and documented error responses |
| **Bonus:** Integration tests with a real database | `@SpringBootTest` suites on H2, plus a Testcontainers suite on real PostgreSQL + Redis |
| README with setup and database configuration | This file |
| PostgreSQL schema / dump | [`database/schema.sql`](database/schema.sql) and [`database/taskdb-dump.sql`](database/taskdb-dump.sql) |

---

## Frontend

A lightweight single-page UI written in plain HTML, CSS and JavaScript (no build step, no npm). It lives in `src/main/resources/static` and is served by Spring Boot at http://localhost:8080, so starting the backend also starts the frontend.

It talks to the same REST API and supports:

- sign in (one-click demo accounts), registration and sign out
- task list with search, status / priority / assignee filters, sorting and pagination
- create and edit tasks in a dialog with field-level validation messages from the API
- inline status changes; delete (admins only)
- employee management for admins (promote / demote roles)
- automatic return to the sign-in screen when the token expires

The UI respects the same rules as the API: a regular user never sees delete buttons, the employee screen or other employees' tasks, but the API enforces those rules regardless of the client.

---

## API reference

Interactive documentation: **http://localhost:8080/swagger-ui.html**. Log in with `POST /api/auth/login`, click **Authorize**, and paste the `accessToken`.

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create an account (role USER) and receive a token |
| POST | `/api/auth/login` | public | Exchange email + password for a JWT |
| GET | `/api/auth/me` | authenticated | Current user's profile |
| GET | `/api/tasks` | authenticated | List tasks (paged, sortable, filterable) |
| GET | `/api/tasks/{id}` | authenticated | Get one task |
| POST | `/api/tasks` | authenticated | Create a task |
| PUT | `/api/tasks/{id}` | authenticated | Replace a task |
| PATCH | `/api/tasks/{id}/status` | authenticated | Change only the status |
| DELETE | `/api/tasks/{id}` | **ADMIN** | Delete a task |
| GET | `/api/users` | **ADMIN** | List employees (paged, sortable) |
| GET | `/api/users/{id}` | **ADMIN** | Get one employee |
| PATCH | `/api/users/{id}/role` | **ADMIN** | Change an employee's role |

### Example session (curl)

```bash
# 1. Log in
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@taskmanager.com","password":"Admin@123"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 2. Create a task assigned to Alice (user id 2)
curl -s http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Prepare Q3 report","description":"Use the finance template","priority":"HIGH","assigneeId":2,"dueDate":"2030-01-31"}'

# 3. Page through tasks, most urgent first
curl -s "http://localhost:8080/api/tasks?page=0&size=5&sort=priority,desc&status=TODO" \
  -H "Authorization: Bearer $TOKEN"
```

Login response:

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": 1, "fullName": "System Admin", "email": "admin@taskmanager.com", "role": "ADMIN", "createdAt": "..." }
}
```

Task response:

```json
{
  "id": 9,
  "title": "Prepare Q3 report",
  "description": "Use the finance template",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2030-01-31",
  "overdue": false,
  "assignee":  { "id": 2, "fullName": "Alice Johnson", "email": "alice@taskmanager.com" },
  "createdBy": { "id": 1, "fullName": "System Admin", "email": "admin@taskmanager.com" },
  "version": 0,
  "createdAt": "2026-10-28T07:56:13.639Z",
  "updatedAt": "2026-10-28T07:56:13.639Z"
}
```

Field rules: `title` 3–150 characters (required), `description` up to 2000, `priority` one of `LOW, MEDIUM, HIGH, CRITICAL` (required), `status` one of `TODO, IN_PROGRESS, DONE` (defaults to `TODO` on create, required on PUT), `dueDate` as `yyyy-MM-dd` and not in the past.

---

## Access rules

| Action | ADMIN | USER |
|---|---|---|
| List / view tasks | all tasks | only tasks they **created** or are **assigned** to |
| Create a task | for anyone (or unassigned) | only for themselves (assignee defaults to the caller) |
| Update a task / change status | any task | only their own tasks (as defined above) |
| Change a task's assignee | yes | no (403) |
| Delete a task | yes | no (403) |
| Manage employees (`/api/users/**`) | yes (cannot change own role) | no (403) |

Rules are enforced in three layers: URL rules in `SecurityConfig`, `@PreAuthorize` on admin-only controller methods, and ownership checks in `TaskService` (a user opening someone else's task gets **403**).

---

## Pagination, sorting and filtering

`GET /api/tasks` accepts:

| Parameter | Example | Notes |
|---|---|---|
| `page` | `0` | zero-based, default 0 |
| `size` | `10` | default 10, maximum 100 |
| `sort` | `priority,desc` | repeatable; allowed: `id`, `title`, `status`, `priority`, `dueDate`, `createdAt`, `updatedAt`; default `createdAt,desc` |
| `status` | `IN_PROGRESS` | exact match |
| `priority` | `HIGH` | exact match |
| `assigneeId` | `2` | exact match |
| `search` | `report` | case-insensitive match on title or description (max 100 characters) |

Response envelope:

```json
{
  "content": [ ... ],
  "page": 0, "size": 5, "totalElements": 8, "totalPages": 2,
  "first": true, "last": false,
  "sort": ["priority,desc", "id,asc"]
}
```

- Sorting by `priority` follows business order (CRITICAL > HIGH > MEDIUM > LOW), not alphabetical order.
- `id` is appended as a tie-breaker so rows never jump between pages when sort values are equal.
- Unknown sort properties are rejected with 400 instead of causing a server error.

---

## Error responses

Every error, whether it comes from validation, a service or the security filter chain, uses the same shape:

```json
{
  "timestamp": "2026-10-28T07:24:20.979Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tasks",
  "validationErrors": [
    { "field": "dueDate",  "message": "Due date cannot be in the past" },
    { "field": "priority", "message": "Priority is required" },
    { "field": "title",    "message": "Title is required" }
  ]
}
```

| Status | When | Example `message` |
|---|---|---|
| 400 | Bean Validation failure, malformed JSON, unknown enum value, bad parameter type, invalid sort property | `Validation failed` (with `validationErrors`), `Cannot sort by 'password'. Allowed sort properties: [...]` |
| 401 | Missing, invalid or expired token; wrong login credentials | `Authentication is required to access this resource`, `Access token has expired`, `Invalid email or password` |
| 403 | Authenticated but not allowed | `You do not have permission to perform this action`, `You do not have permission to access this task` |
| 404 | Unknown task / user / endpoint | `Task not found with id 42` |
| 405 | Wrong HTTP method | `Method 'PATCH' is not supported.` |
| 409 | Duplicate email, or a stale `version` on update | `Task 5 was modified by someone else (your version: 0, current version: 1). Reload it and try again.` |

---

## Database

**Schema initialisation:** Flyway runs `src/main/resources/db/migration/V1__init_schema.sql` at start-up. The SQL is portable, so the same migration runs on H2 (PostgreSQL compatibility mode) and on PostgreSQL. Hibernate is set to `ddl-auto=validate`, so the application refuses to start if the entities and the schema ever drift apart.

| Table | Columns |
|---|---|
| `users` | `id`, `full_name`, `email` (unique), `password` (BCrypt), `role` (`ADMIN`/`USER`), `created_at`, `updated_at` |
| `tasks` | `id`, `title`, `description`, `status`, `priority` (1–4), `due_date`, `assignee_id` → users (`ON DELETE SET NULL`), `created_by` → users, `version`, `created_at`, `updated_at` |

Indexes exist on `assignee_id`, `created_by`, `status` and `due_date`; CHECK constraints guard `role`, `status` and `priority`.

**PostgreSQL files** (in [`database/`](database)):

- [`schema.sql`](database/schema.sql): the DDL, commented. Optional, because Flyway creates the same schema automatically. If you load it manually, the app baselines Flyway and starts normally.
- [`taskdb-dump.sql`](database/taskdb-dump.sql): a `pg_dump` (schema + demo data + Flyway history) taken from the Docker Compose stack. Restore with `psql -U postgres -d taskdb -f database/taskdb-dump.sql`.

| Profile | Database | How to activate |
|---|---|---|
| default | H2 in-memory (`jdbc:h2:mem:taskdb`), reset on every restart | nothing to do |
| `postgres` | PostgreSQL from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `SPRING_PROFILES_ACTIVE=postgres` |

---

## Caching

- **What:** `TaskService.getTasks` (the read-heavy list endpoint) is annotated with `@Cacheable`.
- **Key:** visibility scope + filters + page + sort, e.g. `taskList::user:2|status=TODO|priority=*|assignee=*|search=*|page=0|size=10|sort=dueDate: ASC`. Admins share one scope (they see the same data); each user gets their own, so cached results never leak between users.
- **Eviction:** create, update, status change and delete use `@CacheEvict(allEntries = true)`, so no stale page can survive a write. Redis eviction uses `SCAN` instead of the blocking `KEYS` command.
- **Consistency:** the caching advice is ordered to wrap the transaction, so eviction happens only after the write has committed. A cache hit doesn't open a database transaction at all.
- **Storage:** values are stored as readable JSON (`Jackson2JsonRedisSerializer` bound to `PageResponse<TaskResponse>`) with a 10-minute TTL (`TASK_LIST_CACHE_TTL`).
- **Resilience:** if Redis is unavailable, cache errors are logged and requests fall back to the database (`LoggingCacheErrorHandler`).
- **Providers:** the `redis` profile (used by Docker Compose) uses Redis. The default profile uses a simple in-memory cache, so local runs need no Redis.

Inspect the cache in the Compose stack: `docker compose exec redis redis-cli --scan --pattern 'taskList::*'`

---

## Security design

1. `POST /api/auth/login` authenticates through Spring Security's `AuthenticationManager` (`DaoAuthenticationProvider` + BCrypt) and returns an HMAC-SHA signed JWT (subject = email; claims: user id, role, issuer, issued-at, expiry; 1 hour by default).
2. `JwtAuthenticationFilter` (a `OncePerRequestFilter`) validates the `Authorization: Bearer` token on each request (signature, expiry, issuer), **reloads the user from the database** (so role changes and deleted accounts take effect immediately) and populates the `SecurityContext`.
3. Sessions are stateless (`SessionCreationPolicy.STATELESS`). CSRF is disabled because no cookies are used.
4. `RestAuthenticationEntryPoint` (401) and `RestAccessDeniedHandler` (403) write the same JSON error body as the controllers, including the reason a token was rejected (expired vs invalid).
5. Passwords are hashed with BCrypt. Emails are normalised to lower case. Login failures don't reveal whether an email exists.
6. The signing key comes from `JWT_SECRET` (Base64, at least 256 bits). The application refuses to start with a weaker key.
7. CORS is enabled for `/api/**` for configurable origins (`CORS_ALLOWED_ORIGINS`), for frontends hosted elsewhere.

---

## Testing

```bash
./mvnw test        # or: mvnw.cmd test
./mvnw verify      # same, as run by CI
```

56 tests across four levels:

| Level | Classes | What they prove |
|---|---|---|
| Unit (JUnit 5 + Mockito) | `TaskServiceTest` (18), `AuthServiceTest` (4), `JwtServiceTest` (6), `TaskListKeyGeneratorTest` (3) | Business and access rules, version conflicts, due-date rules, token expiry / tampering / wrong issuer / weak keys, cache-key isolation |
| Web slice (`@WebMvcTest`) | `TaskControllerTest` (11) | Real security rules and error handling with a mocked service: 201 + `Location`, 400 field errors, 401, 403, 404, parameter binding, default paging |
| Integration (`@SpringBootTest`, H2 + Flyway) | `TaskManagementIntegrationTest` (10), `ApplicationStartupTest` (3) | Full flows with real JWTs: lifecycle, consistent 401/403/404, validation, pagination and sorting, visibility rules, cache eviction, **no N+1 queries** (asserted with Hibernate statistics), registration, role changes, demo data, UI and docs served |
| Integration (Testcontainers) | `PostgresRedisIntegrationTest` (1) | Flyway + Hibernate validation on **real PostgreSQL 16**, task lists cached as JSON in **real Redis 7**, served from the cache, evicted on writes |

- The Testcontainers suite needs Docker. Without Docker it is **skipped automatically** and the other 55 tests still run.
- A JaCoCo coverage report is written to `target/site/jacoco/index.html`.
- GitHub Actions runs the full suite (including Testcontainers) and builds the Docker image on every push.

---

## Configuration

All settings have working defaults. Override them with environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | *(none)* | `postgres`, `redis` or both |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/taskdb` / `taskuser` / `taskpass` | PostgreSQL connection (`postgres` profile) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection (`redis` profile) |
| `JWT_SECRET` | development key | Base64 HMAC key, at least 32 bytes. **Change it outside local development.** |
| `JWT_EXPIRATION` | `PT1H` | Token lifetime (ISO-8601 duration) |
| `TASK_LIST_CACHE_TTL` | `PT10M` | Redis TTL for cached task pages |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origins allowed to call the API from a browser |
| `APP_SEED_ENABLED` | `true` | Create demo users and tasks when the database is empty |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | `admin@taskmanager.com` / `Admin@123` | Seeded admin account |
| `APP_DEMO_USER_PASSWORD` | `User@1234` | Password of the seeded employees |
| `SWAGGER_ENABLED` | `true` | Expose Swagger UI and the OpenAPI document |

Docker Compose only: `APP_PORT` (host port of the app, default 8080) and `POSTGRES_PORT` (default 5433).

---

## Project structure

```
src/main/java/com/harsh/taskmanager
├── TaskManagerApplication.java
├── config/        CacheConfig, RedisCacheConfig, TaskListKeyGenerator, OpenApiConfig, ClockConfig, DataSeeder
├── controller/    AuthController, TaskController, UserController
├── dto/           request / response records (validated), PageResponse, TaskFilter
├── entity/        User, Task, Role, TaskStatus, TaskPriority (+ converter)
├── exception/     ApiError, GlobalExceptionHandler, custom exceptions
├── mapper/        TaskMapper
├── repository/    UserRepository, TaskRepository, TaskSpecifications
├── security/      SecurityConfig, JwtService, JwtAuthenticationFilter, UserPrincipal, entry point / access-denied handler
├── service/       AuthService, TaskService, UserService
└── util/          SortValidator
src/main/resources
├── application.yml, application-postgres.yml, application-redis.yml
├── db/migration/V1__init_schema.sql
└── static/        web UI (index.html, css/, js/)
src/test/java/...  unit, web-slice and integration tests
database/          schema.sql, taskdb-dump.sql
Dockerfile, docker-compose.yml, .github/workflows/ci.yml
```

---

## Design decisions

- **DTOs instead of entities in the API.** Request records carry validation and response records control exactly what is exposed (no password hashes, no lazy-loading surprises). `spring.jpa.open-in-view` is off, and mapping happens inside service transactions.
- **N+1 prevention.** The list query uses an `@EntityGraph` to fetch assignee and creator in the same SQL statement. An integration test asserts the statement count.
- **Dynamic filters with JPA Specifications.** Each filter is a small composable `Specification`. LIKE wildcards in search text are escaped.
- **Priority stored as a number** through an `AttributeConverter` with explicit levels (not the enum ordinal), so `ORDER BY priority` is meaningful and reordering the enum can't corrupt data.
- **Optimistic locking.** `@Version` on `Task` protects against concurrent writes, and clients can send the `version` they last read on `PUT` to get a 409 instead of silently overwriting newer data.
- **403 vs 404 for other people's tasks.** The API returns 403 when a task exists but belongs to someone else, which makes the access rules explicit for this assignment. Returning 404 instead (to hide existence) would be a one-line change in `TaskService.assertCanAccess`.
- **Users reloaded on every request.** The JWT stays small and role changes apply immediately, at the cost of one indexed lookup per request.
- **Injectable `Clock`.** Token expiry, overdue flags and due-date checks are deterministic in tests.
