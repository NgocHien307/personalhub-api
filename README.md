# PersonalHub API — Quản lý dữ liệu cuộc sống cá nhân

**Java 21 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · Spring Data JPA · Spring Security · Docker**

PersonalHub hướng tới một API tập trung để lưu trữ và khai thác dữ liệu cuộc sống cá nhân từ nhiều dịch vụ. Backend đồng bộ dữ liệu từ các nguồn bên ngoài, chuẩn hóa và lưu vào PostgreSQL, sau đó cung cấp REST API trả về JSON để ứng dụng hoặc dashboard có thể sử dụng.

**Phạm vi hiện tại:** đã tích hợp lịch sử commit/repository GitHub và tasks từ Notion, có dashboard xem dữ liệu, lọc/phân trang và đồng bộ thủ công; API tổng quan và mã request hỗ trợ tra lỗi. Các nguồn như wearable, sách đã đọc và log tập luyện là hướng phát triển tiếp theo. Dự án hiện chưa có OAuth Login hoặc hỗ trợ nhiều người dùng.

**Mở dashboard:** [http://localhost:8080/dashboard](http://localhost:8080/dashboard), nhập PERSONALHUB_API_KEY trong `.env`. Giao diện chạy cùng Spring Boot/Docker Compose. Xem [hướng dẫn và ảnh](docs/DASHBOARD_UI_VI.md), [kết quả kiểm tra](docs/DASHBOARD_UI_VERIFICATION.md).

**Notion:** xem [hướng dẫn cấu hình và REST API](docs/NOTION_INTEGRATION_VI.md). Notion mặc định tắt, có thể bật độc lập với GitHub scheduler. Xem [kết quả xác minh bản bàn giao](docs/VERIFICATION.md).

**Kiểm tra dữ liệu thật:** [task list cho Notion/GitHub Issue](docs/checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md), [CSV đầu việc](docs/checklists/PersonalHub.tasks.notion.csv), [file commit thử](docs/checklists/github-live-test.sample.md). Module GitHub đồng bộ repository/commit; Issue dùng để theo dõi checklist.

**API:** [hướng dẫn OpenAPI/Postman và response lỗi](docs/API_GUIDE_VI.md), [OpenAPI 3.1](src/main/resources/openapi/personalhub-openapi.json), [collection Postman](postman/PersonalHub.postman_collection.json), [environment mẫu](postman/PersonalHub.local.example.postman_environment.json). GET `/api/v1/openapi.json` phục vụ cùng contract và yêu cầu API key.

**Tổng quan và tra lỗi:** GET `/api/v1/dashboard/overview?days=30` tổng hợp cache GitHub/Notion và run gần nhất. Response có `X-Request-ID` để đối chiếu log; lỗi Security/MVC có thêm requestId. Xem [hướng dẫn sử dụng](docs/DASHBOARD_OVERVIEW_VI.md).

**Vận hành và phát hành:** [backup/restore, HTTPS và triển khai server](docs/OPERATIONS_VI.md), [quy trình release](docs/RELEASE_VI.md), [bằng chứng kiểm thử vận hành](docs/OPERATIONS_VERIFICATION.md). Các công cụ Python dùng thư viện chuẩn, không thêm dependency cho API.

**Độ ổn định GitHub:** đã bổ sung retry có giới hạn, tôn trọng rate-limit headers và chuyển các run bị gián đoạn sang FAILED khi khởi động lại. Xem [quy tắc vận hành](docs/GITHUB_SYNC_RELIABILITY_VI.md) và [kết quả kiểm tra](docs/GITHUB_SYNC_RELIABILITY_VERIFICATION.md). Chạy một API instance trên mỗi database.

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
DB_URL=jdbc:postgresql://localhost:5433/personalhub
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
| GET | `/dashboard` | Dashboard public shell; dữ liệu bên trong vẫn cần API key |
| GET | `/api/v1/openapi.json` | OpenAPI contract, yêu cầu X-API-Key |
| GET | `/api/v1/dashboard/overview?days=30` | Tổng quan GitHub/Notion từ cache và run gần nhất; không trigger sync |
| POST | `/api/v1/sync/github` | Submit async sync request (202) |
| GET | `/api/v1/sync/runs` | Last sync runs |
| GET | `/api/v1/sync/runs/{id}` | Status of one run |
| GET | `/api/v1/github/repositories?limit=20` | Tracked repos |
| GET | `/api/v1/github/commits?page=0&size=20&repoId=123` | Stored commits; repoId optional |
| GET | `/api/v1/github/stats?days=30` | UTC activity statistics |
| POST | `/api/v1/notion/sync?full=false` | Sync Notion trong request: 200 thành công, 502 thất bại, 409 đang sync |
| GET | `/api/v1/notion/tasks?page=0&size=20` | Tasks đã cache; hỗ trợ status và includeArchived |
| GET | `/api/v1/notion/tasks/{id}` | Chi tiết task thuộc source hiện tại |
| GET | `/api/v1/notion/sync-runs?limit=20` | Lịch sử sync Notion |
| GET | `/api/v1/notion/sync-runs/{id}` | Chi tiết một lần sync Notion |

Tất cả `/api/v1/**` yêu cầu header `X-API-Key`.

Lỗi dùng `application/problem+json` với `title`, `status`, `detail`, `instance`, `code`; Notion sync FAILED trả 502 kèm `syncRunId` để tra lịch sử. Đây là thay đổi định dạng so với response lỗi cũ; xem [hướng dẫn tương thích](docs/API_GUIDE_VI.md#4-thay-đổi-tương-thích). Trong Postman, biến `runSync=false` mặc định bỏ qua các request trigger sync.

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
Notion bổ sung V2 với `notion_tasks`, `notion_sync_states` và `notion_sync_runs`; dùng cùng PostgreSQL, không sửa V1 hay bảng GitHub.
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
9. GitHub GET requests retry temporary 5xx/network failures and 429/rate-limited 403, with at most 3 attempts per page. Retry waits honor `Retry-After` and primary rate-limit reset headers; a wait over 60 seconds fails the job instead of retrying early. Permanent client errors fail immediately. See [retry and restart behavior](docs/GITHUB_SYNC_RELIABILITY_VI.md).
10. Before accepting sync requests on startup, the single-instance GitHub coordinator marks existing `QUEUED`/`RUNNING` runs `FAILED`, recording a restart message and finish time. It preserves completed data/checkpoints and terminal run history. Trigger a new sync to continue; interrupted jobs are not automatically replayed. Notion also recovers interrupted runs under its source advisory lock at startup and before a new sync; see [Notion recovery](docs/NOTION_RECOVERY_VI.md).
11. `recordedCommits` counts all commit entries in the tracked repositories; `authoredCommits` filters by GitHub `author.login`. Some Git commits may not map to a GitHub user; don't equate these counts with GitHub contribution graphs.
12. Stats use UTC for day boundaries. You can change to the user's timezone in the next version.
13. `GITHUB_TOKEN` is optional for public repository reads, but authenticated GitHub requests generally have more generous API rate limits. **This endpoint remains public-only even if a token is provided**. Never commit a real token.

14. Đổi `GITHUB_USERNAME` sau khi đã đồng bộ sẽ không tự xóa dữ liệu của username cũ. Khi thử một tài khoản khác trên môi trường local, hãy chủ động tạo database/volume mới (lệnh xóa volume sẽ **xóa toàn bộ dữ liệu**).
15. HTTP client có connect timeout 5 giây và read timeout 20 giây. GitHub GET retry tối đa 3 lần khi lỗi kết nối/timeout; nếu vẫn lỗi thì run FAILED. Lịch sử/log GitHub lưu thông báo lỗi an toàn, không lưu raw body/headers từ upstream.

## 6. Tests

```bash
mvn test        # fast unit tests, no Docker required
mvn verify      # unit + Testcontainers PostgreSQL integration test; Docker required
node --test src/test/frontend/dashboard-core.test.mjs  # Node 24; client tests, no npm install
```

- `GitHubRestClientTest` / `GitHubRetryTest`: pagination, commit timestamps, retry/backoff, rate-limit headers, bounded attempts, interruption and error redaction
- `GitHubPersistenceServiceTest`: maps payload and saves checkpoint
- `GitHubSyncCoordinatorTest` / `GitHubSyncWorkerTest`: concurrent jobs, rejected submissions, busy-guard release, checkpoints, partial failures and safe logging
- `ApiKeyFilterTest`: tests API-key authentication and context cleanup
- `RequestIdFilterTest` / `ApiContractIT`: request IDs, safe completion logs, MDC cleanup and correlation through Security/MVC
- `DashboardOverviewServiceTest` / `DashboardControllerTest` / `DashboardOverviewIT`: whole-cache counts, source scoping, disabled/empty Notion, latest failed runs and validation without upstream calls
- `DashboardPageIT` và client tests: public shell/assets không mở API dữ liệu; CSP, session/timeout, URL an toàn và ngày giờ
- `GitHubPersistenceIT` / `GitHubSyncRecoveryIT`: actual PostgreSQL, idempotency, transaction rollback, startup recovery and preserved terminal history
- Notion unit tests: configuration, JSON/pagination, HTTP retry and redaction, mapping, sync/checkpoint, REST validation
- `NotionPersistenceIT`: PostgreSQL upsert, rollback, distributed lock, Flyway and Spring Security; see [Notion guide](docs/NOTION_INTEGRATION_VI.md) for an external test database option

GitHub MVP đã được kiểm tra trực tiếp với GitHub public và PostgreSQL: **4/4 PASS**, gồm sync SUCCESS, đối chiếu dữ liệu, sync lặp và commit mới. Xem [báo cáo và SHA kiểm tra](docs/GITHUB_MVP_VERIFICATION.md).

Notion thật **6/6 PASS**: sync SUCCESS, dữ liệu PostgreSQL/REST khớp nguồn, cập nhật giữ nguyên ID, tạo mới tăng 5 → 6, full sync lặp giữ 6 → 6 và scheduler tự tạo job SUCCESS. Xem [báo cáo và bằng chứng](docs/NOTION_LIVE_VERIFICATION.md).

## 7. Next iterations (outside this MVP)

- GitHub OAuth Login; multi-user with encrypted credential storage
- GitHub webhook + signature validation
- Historical sync metrics and distributed locking/leases for multiple API replicas
- Cursor-based pagination, per-user timezone và dashboard caching
- Tích hợp thêm dữ liệu wearable/sức khỏe, sách đã đọc và log tập luyện
- Notion delete reconciliation, webhooks và automated insights
- Historical trends và metrics sync; dashboard hiện có đã đọc cache và hỗ trợ đồng bộ thủ công
- Interactive Swagger UI backed by the maintained OpenAPI contract

## 8. Safety and scope

Only expose Docker ports on `127.0.0.1` by default. Use HTTPS and proper access control if deploying remotely. `X-API-Key` is a deliberate single-user MVP mechanism, **not** a full user management solution. No real credentials are committed. Rotate keys if exposed.
