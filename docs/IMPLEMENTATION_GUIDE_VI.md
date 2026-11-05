# Hướng dẫn đọc và triển khai mã nguồn PersonalHub API

Bản code này được tổ chức để học từng lớp backend riêng biệt. **Chạy được hay chưa trên máy bạn cần được xác nhận bằng `mvn test`, `mvn verify` và `docker compose up --build`**. Xem README ở thư mục gốc để biết đầy đủ lệnh chạy.

## Bước 1 — Maven + Spring Boot

Mở `pom.xml`. Các dependency cần chú ý:

- `spring-boot-starter-webmvc`: REST Controller (Spring MVC).
- `spring-boot-starter-restclient`: auto-configure `RestClient.Builder` dùng gọi GitHub API. Với Spring Boot 4, dependency này không tự đi kèm `webmvc`.
- `spring-boot-starter-data-jpa`: JPA repositories, Hibernate và JDBC.
- `spring-boot-starter-flyway` + `flyway-database-postgresql`: migration PostgreSQL.
- `spring-boot-starter-security`: xác thực API key đơn giản cho MVP.
- `spring-boot-starter-test` và các test starters: JUnit, Mockito, MockMvc.
- `testcontainers-junit-jupiter` + `testcontainers-postgresql`: kiểm thử với database thật.

`src/main/java/com/personalhub/PersonalHubApplication.java` là entry point. `@ConfigurationPropertiesScan` đọc `app.github.*` vào `GitHubProperties`.

## Bước 2 — PostgreSQL và Flyway

Chạy `docker compose up -d db`. Trong PostgreSQL, Flyway chạy `V1__initial_schema.sql` khi application khởi động:

- `github_repositories`: một dòng cho mỗi GitHub repository; PK là GitHub repository ID.
- `github_commits`: commit lưu theo PK gồm `(repository_id, sha)`. Nhờ vậy cùng một SHA trong hai repo khác nhau vẫn được lưu độc lập.
- `sync_states`: checkpoint theo repository, cho phép đồng bộ repo A thành công dù repo B gặp lỗi.
- `sync_runs`: nhật ký từng sync job từ `QUEUED` đến `SUCCESS` hoặc `FAILED`.

Không bật `spring.jpa.hibernate.ddl-auto=update`: để Flyway sở hữu thay đổi schema, Hibernate chỉ `validate`.

## Bước 3 — External API adapter

Mở `GitHubApiClient.java` (interface) và `GitHubRestClient.java` (implementation). `RestClient` gọi:

```text
GET https://api.github.com/users/{username}/repos
GET https://api.github.com/repos/{owner}/{repo}/commits?since=...
```

Trong `GitHubRestClient`, `GitHubRepoDto` và `GitHubCommitDto` đại diện cho **hình dạng JSON bên ngoài**; không dùng trực tiếp những DTO này làm JPA entities. Đây là một anti-corruption layer đơn giản: GitHub API thay đổi không nhất thiết ảnh hưởng database hoặc API public của chúng ta.

Các điểm kỹ thuật: `per_page=100`, kiểm tra `Link` header có `rel="next"`, giới hạn `max-pages`, xử lý empty repo HTTP 409 và chỉ thêm Bearer token khi đã cấu hình. Lỗi khác sẽ nổi lên và khiến job đánh dấu FAILED. Chưa có automatic backoff.

## Bước 4 — Entities và Repositories

Mở các entity trong `github/domain`:

```text
GitHubRepositoryEntity    PK: id (Long)
GitHubCommitEntity        PK: GitHubCommitId (repositoryId, sha)
SyncStateEntity           PK: repositoryId (Long)
```

Các repository Spring Data JPA trong `github/repository` hỗ trợ lưu/đọc. `GitHubQueryService` còn dùng `JdbcTemplate` cho các aggregate PostgreSQL cụ thể như `COUNT(DISTINCT date)`.

Trong `GitHubPersistenceService.persist(...)`, Spring `@Transactional` áp dụng cho **một repository mỗi lần**: lưu thông tin repo, lưu commits, cập nhật checkpoint. Nếu lưu bị lỗi, tất cả thao tác của repo đó rollback, checkpoint không được tăng.

## Bước 5 — Async sync orchestration

Luồng gọi:

```text
POST /api/v1/sync/github
  -> GitHubSyncCoordinator.requestSync()
     -> create sync_runs (QUEUED)
     -> GitHubSyncWorker.runAsync(...)
        -> status RUNNING
        -> GitHub REST API: fetch repositories, commits
        -> GitHubPersistenceService.persist(...) per repository
        -> status SUCCESS/FAILED
```

Một `AtomicBoolean` ngăn các lần sync chồng lấn **trong cùng JVM**. Đây không phải distributed lock; nếu scale nhiều instance cần cơ chế locking khác.

`GitHubSyncScheduler` chỉ hoạt động khi `SYNC_ENABLED=true`, mặc định tắt để tránh gọi API ngoài ý muốn. Trường hợp manual sync thì vẫn hoạt động.

## Bước 6 — REST API

Mở `GitHubController.java` và `SyncController.java`. API trả JSON từ database, không gọi GitHub trong mỗi GET request.

```text
POST /api/v1/sync/github       202 Accepted, trả sync run ID
GET  /api/v1/sync/runs/{id}    status/counts/error
GET  /api/v1/github/repositories
GET  /api/v1/github/commits?repoId=...&page=0&size=20
GET  /api/v1/github/stats?days=30
```

Bảo mật: `SecurityConfig` áp dụng stateless API-key filter cho tất cả `/api/v1/**`. Health check `/actuator/health` mở để docker/monitoring đọc. Chỉ dùng trên localhost trong bản demo; với public deployment phải dùng HTTPS và nâng cấp authentication.

## Bước 7 — Tests

```bash
mvn test
mvn verify
```

**Unit tests** (không cần Docker):

1. `GitHubRestClientTest`: mock HTTP của GitHub, pagination, JSON, repo rỗng.
2. `GitHubPersistenceServiceTest`: mapper + JPA repository invocation.
3. `GitHubSyncCoordinatorTest`: từ chối sync đồng thời.
4. `GitHubControllerTest`: validate input trả HTTP 400.
5. `ApiKeyFilterTest`: header API-key và cleanup security context.

**Integration test** (`*IT`, cần Docker):

`GitHubPersistenceIT` tạo PostgreSQL bằng Testcontainers, chạy Flyway, lưu cùng commit hai lần, xác minh chỉ có một bản ghi và checkpoint đổi đúng. Maven Failsafe thực thi integration test trong giai đoạn `verify`; `test` thông thường chỉ chạy fast tests.

## Bước 8 — Kết quả đầu tiên bạn cần quan sát

Sau khi `POST /api/v1/sync/github`, gọi `GET /api/v1/sync/runs` đến khi xuất hiện `SUCCESS`. Gọi `GET /api/v1/github/repositories` để thấy repo, `GET /api/v1/github/commits` để thấy commits. Stats có thể bằng 0 nếu repo chưa có commit trong khoảng `SYNC_INITIAL_DAYS` hoặc commit không map được tới GitHub login của bạn.

## Sprint kế tiếp

GitHub bounded retry/rate-limit backoff và startup recovery đã hoàn thành trong mốc 04/11/2026; xem [cơ chế và giới hạn](GITHUB_SYNC_RELIABILITY_VI.md), [báo cáo kiểm tra](GITHUB_SYNC_RELIABILITY_VERIFICATION.md). Các hạng mục còn lại:

1. Backup/restore PostgreSQL và triển khai HTTPS. [Dashboard](DASHBOARD_UI_VI.md), API tổng quan và Request ID đã được bổ sung; metrics sync có lưu lịch sử vẫn là backlog.
2. Distributed lease/locking trước khi chạy nhiều replica. Recovery Notion đã được bổ sung ở [mốc tiếp theo](NOTION_RECOVERY_VI.md).
3. Cursor-based pagination, query filtering, testing đầy đủ hơn.
4. GitHub OAuth2 / token management. Notion Integration đã được bổ sung trong Sprint 2; xem [hướng dẫn Notion](NOTION_INTEGRATION_VI.md).
5. Metrics/trends và trải nghiệm nhiều người dùng nếu mở rộng; dashboard hiện tại chạy cùng Spring Boot, xem hướng dẫn UI.
