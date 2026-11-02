# PersonalHub API — Spring Boot GitHub MVP

**Java 21 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · Spring Data JPA · Spring Security · Docker**

Backend portfolio project tổng hợp dữ liệu commit và repository GitHub. Sprint 1 + GitHub MVP, **chưa** có Notion, OAuth Login, frontend và multi-user.

## 1. Quick start (chỉ cần Docker Desktop)

```bash
cp .env.example .env
```

Mở `.env`, đổi `GITHUB_USERNAME` thành username GitHub của bạn, đặt `POSTGRES_PASSWORD` thành mật khẩu ngẫu nhiên dài và thay `PERSONALHUB_API_KEY` bằng một secret dài, khó đoán. Với Windows PowerShell, chạy `Copy-Item .env.example .env`.

```bash
docker compose up --build -d
docker compose logs -f api
```

Chờ log thông báo Spring Boot đã khởi động. Kiểm tra health:

```bash
curl http://localhost:8080/actuator/health
```

Gửi một sync job (thay `<YOUR_API_KEY>` bằng giá trị trong `.env`):

```bash
curl -i -X POST http://localhost:8080/api/v1/sync/github   -H "X-API-Key: <YOUR_API_KEY>"
```

Nhận HTTP `202 Accepted` và một `id`. Kiểm tra trạng thái và dữ liệu:

```bash
curl -H "X-API-Key: <YOUR_API_KEY>" http://localhost:8080/api/v1/sync/runs
curl -H "X-API-Key: <YOUR_API_KEY>" http://localhost:8080/api/v1/github/repositories
curl -H "X-API-Key: <YOUR_API_KEY>" 'http://localhost:8080/api/v1/github/commits?page=0&size=20'
curl -H "X-API-Key: <YOUR_API_KEY>" 'http://localhost:8080/api/v1/github/stats?days=30'
```

> PowerShell: dùng `curl.exe` thay cho `curl`, vì trên một số phiên bản Windows `curl` là alias của PowerShell.

### Chạy backend từ IDE (PostgreSQL dùng Docker)

```bash
docker compose up -d db
```

Đặt các environment variables trong IntelliJ / terminal:

```text
GITHUB_USERNAME=your-github-name
PERSONALHUB_API_KEY=your-long-random-secret
DB_URL=jdbc:postgresql://localhost:5432/personalhub
DB_USERNAME=personalhub
DB_PASSWORD=<POSTGRES_PASSWORD from .env>
```

Sau đó `mvn spring-boot:run` hoặc chạy `PersonalHubApplication` trong IDE.

### Windows PowerShell example

```powershell
Copy-Item .env.example .env
# Edit .env
# Start all services
docker compose up --build -d
# Replace the key below with your .env value
$h = @{ "X-API-Key" = "your-long-random-secret" }
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/sync/github' -Headers $h
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/sync/runs' -Headers $h
```

## Troubleshooting: Spring Boot 4 / Jackson 3

Nếu ứng dụng báo lỗi `Failed to bind properties under spring.jackson.json.write` hoặc `No enum constant ... JsonWriteFeature.dates-as-timestamps`, hãy chắc chắn rằng `src/main/resources/application.yml` **không chứa** block `spring.jackson.json.write.dates-as-timestamps`. Bản zip sửa lỗi đã loại bỏ block này. Jackson 3 mặc định xuất ngày giờ dạng ISO-8601, nên không cần thiết lập bổ sung. Nếu thực sự cần cấu hình tường minh, dùng `spring.jackson.datatype.datetime.write-dates-as-timestamps=false` theo tài liệu Spring Boot 4.

Sau khi sửa cấu hình:

```bash
docker compose up -d --build --force-recreate api
docker compose logs --tail=100 -f api
```

Không cần `docker compose down -v` vì lệnh đó xóa volume PostgreSQL.

## 2. Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/actuator/health` | Health check, public |
| POST | `/api/v1/sync/github` | Submit async sync request (202) |
| GET | `/api/v1/sync/runs` | Last sync runs |
| GET | `/api/v1/sync/runs/{id}` | Status of one run |
| GET | `/api/v1/github/repositories?limit=20` | Tracked repos |
| GET | `/api/v1/github/commits?page=0&size=20&repoId=123` | Stored commits; repoId optional |
| GET | `/api/v1/github/stats?days=30` | UTC activity statistics |

Tất cả `/api/v1/**` yêu cầu header `X-API-Key`.

## 3. Architecture

```text
HTTP -> Spring Security API-key filter -> Controllers -> Query Service -> PostgreSQL
                                             |
POST /sync -> Coordinator (AtomicBoolean) -> Async Worker
                                           -> GitHub API Client -> GitHub REST
                                           -> Persistence Service (@Transactional per repo)
                                           -> PostgreSQL + sync_states
```

Package-by-feature:

- `config`: settings, security, async executor
- `github.infrastructure`: external API adapter + DTO
- `github.application`: normalize/persist and query
- `github.domain`: JPA entities and embedded composite ID
- `github.repository`: Spring Data JPA interfaces
- `github.web`: REST controllers + response records
- `sync.application`: coordinator and asynchronous worker
- `sync.domain`: job state
- `sync.scheduler`: fixed-delay trigger
- `sync.web`: submit/status endpoints

## 4. Data model

```text
github_repositories 1 ─── N github_commits
         |               PK(repository_id, sha)
         └── 1:1 sync_states (last_success_at)

sync_runs (UUID, status, started_at, finished_at, counts, error)
```

Flyway migration: `src/main/resources/db/migration/V1__initial_schema.sql`.
JPA uses `ddl-auto: validate` (schema created by Flyway only).

## 5. Sync semantics and limitations

1. GitHub public repositories **owned by** the configured username are fetched with `/users/{username}/repos`.
2. Most recently updated repos are processed first. Only the first `SYNC_MAX_REPOSITORIES` repos are tracked in each run (10 by default). You can increase it. **This MVP does not claim full-account coverage**.
3. First successful run for a repo starts `SYNC_INITIAL_DAYS` (default 30) days in the past.
4. Next runs use `last_success_at - SYNC_OVERLAP_MINUTES` (default 60). The overlap tolerates some late arrivals; it cannot guarantee catching arbitrary historical rewrites.
5. Each repo's commits and its checkpoint are persisted within **one DB transaction**. Failure part-way through a run leaves checkpoints of previously completed repos intact; other repos are safe to retry.
6. GitHub pagination follows `Link: ... rel="next"` page-by-page and fails the sync if a max-page cap is reached. Tune `SYNC_MAX_PAGES` (default 10) rather than silently dropping pages.
7. GitHub may return HTTP 409 for empty repos; these have zero commits.
8. Manual sync uses an async single-thread executor. Duplicate concurrent trigger gets HTTP 409; this guard is **in-memory only**. Running more than one API replica requires distributed locking.
9. If external API fails (including 429, permission issues, network errors), job becomes FAILED with error information. **Automatic HTTP retry/backoff is not implemented in this MVP**; trigger manually again or wait for next scheduled run. Respect GitHub rate limits.
10. A process crash during a run can leave a `QUEUED`/`RUNNING` status; stale-run recovery is a follow-up task.
11. `recordedCommits` counts all commit entries in the tracked repositories; `authoredCommits` filters by GitHub `author.login`. Some Git commits may not map to a GitHub user; don't equate these counts with GitHub contribution graphs.
12. Stats use UTC for day boundaries. You can change to the user's timezone in the next version.
13. `GITHUB_TOKEN` is optional for public repository reads, but authenticated GitHub requests generally have more generous API rate limits. **This endpoint remains public-only even if a token is provided**. Never commit a real token.

14. Đổi `GITHUB_USERNAME` sau khi đã đồng bộ sẽ không tự xóa dữ liệu của username cũ. Khi thử một tài khoản khác trên môi trường local, hãy chủ động tạo database/volume mới (lệnh xóa volume sẽ **xóa toàn bộ dữ liệu**).
15. HTTP client có connect timeout 5 giây và read timeout 20 giây; các request lỗi timeout được ghi nhận là sync FAILED.

## 6. Tests

```bash
mvn test        # fast unit tests, no Docker required
mvn verify      # unit + Testcontainers PostgreSQL integration test; Docker required
```

- `GitHubRestClientTest`: handles multi-page repositories and commit JSON date parsing with Spring MockRestServiceServer
- `GitHubPersistenceServiceTest`: maps payload and saves checkpoint
- `GitHubSyncCoordinatorTest`: disallows concurrent jobs
- `ApiKeyFilterTest`: tests API-key authentication and context cleanup
- `GitHubPersistenceIT`: starts actual PostgreSQL and checks idempotency, Flyway, and checkpoint

## 7. Next iterations (outside this MVP)

- GitHub OAuth Login; multi-user with encrypted credential storage
- GitHub webhook + signature validation
- Automatic bounded retry respecting rate-limit headers, metrics, and distributed lock
- Cursor-based endpoint pagination, per-user timezone and dashboard caching
- Notion integration / reading log / automated insights
- An OpenAPI generation dependency and Swagger UI once a compatible 4.1.x version is pinned

## 8. Safety and scope

Only expose Docker ports on `127.0.0.1` by default. Use HTTPS and proper access control if deploying remotely. `X-API-Key` is a deliberate single-user MVP mechanism, **not** a full user management solution. No real credentials are committed. Rotate keys if exposed.
