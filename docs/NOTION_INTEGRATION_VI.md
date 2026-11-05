# Sprint 2 — Notion Integration

Module đồng bộ một chiều từ một Notion data source sang PostgreSQL của PersonalHub. Giữ Spring Boot 4.1.2, Java 21, Flyway, Spring Security và GitHub MVP. Không cần token Notion để chạy GitHub; Notion mặc định tắt.

## 1. Chuẩn bị Notion

1. Tại [Notion Developer portal](https://www.notion.so/profile/integrations), tạo internal connection/integration cho workspace của bạn và bật quyền **Read content**. Lưu token vào `.env` cục bộ, không đưa vào Git hay trình duyệt frontend.
2. Mở database chứa tasks, vào menu Connections và cấp quyền truy cập cho connection vừa tạo. Thiếu quyền có thể trả 403 hoặc 404, dù ID đúng.
3. Database cần các cột sau; tên cột phân biệt hoa/thường và có thể đổi qua biến môi trường:

| Cột mặc định | Kiểu | Cách lưu |
| --- | --- | --- |
| Name | Title, bắt buộc | Nối tất cả đoạn `plain_text` |
| Status | Status hoặc Select, tùy chọn | Tên trạng thái, có thể null |
| Due | Date, tùy chọn | Giữ nguyên start, end, time_zone; không đổi ngày thuần thành UTC |

4. Lấy **data source ID**, không dùng database ID hoặc view ID. Với database ID lấy từ URL Notion, gọi `GET /v1/databases/{database_id}` bằng cùng token/version và chọn `data_sources[].id` của bảng cần sync. Nếu có nhiều data source, chọn đúng tên. Module nhận UUID dạng có dấu gạch ngang.

Ví dụ PowerShell (token đã đặt trong biến môi trường, không ghi trực tiếp trong lệnh):

```powershell
$headers = @{ Authorization = "Bearer $env:NOTION_TOKEN"; 'Notion-Version' = '2025-09-03' }
$databaseId = Read-Host 'Database ID'
$database = Invoke-RestMethod -Uri "https://api.notion.com/v1/databases/$databaseId" -Headers $headers
$database.data_sources | Select-Object id, name
```

Module pin phiên bản `2025-09-03`, dùng `POST /v1/data_sources/{id}/query`. Không đổi version riêng lẻ mà chưa kiểm tra thay đổi schema. Tài liệu chính thức: [data source query](https://developers.notion.com/reference/query-a-data-source), [hướng dẫn chuyển sang data source](https://developers.notion.com/guides/get-started/upgrade-guide-2025-09-03), [quyền connection](https://developers.notion.com/reference/capabilities).

## 2. Cấu hình và chạy

Tại thư mục có `compose.yaml`:

```powershell
Copy-Item .env.example .env
```

Điền `POSTGRES_PASSWORD` và `PERSONALHUB_API_KEY` bằng giá trị ngẫu nhiên của riêng bạn; API key tối thiểu 16 ký tự. Điền các biến dưới đây trong `.env`:

```dotenv
NOTION_ENABLED=true
NOTION_TOKEN=
NOTION_DATA_SOURCE_ID=
NOTION_TITLE_PROPERTY=Name
NOTION_STATUS_PROPERTY=Status
NOTION_DUE_PROPERTY=Due
NOTION_SYNC_ENABLED=true
NOTION_SYNC_FIXED_DELAY_MS=1800000
```

Hai dòng token/ID ở trên phải được điền trên máy của bạn. Nếu `NOTION_ENABLED=true` mà thiếu token/UUID hợp lệ, ứng dụng từ chối khởi động. `NOTION_ENABLED=true` và `NOTION_SYNC_ENABLED=false` cho phép sync thủ công.

```powershell
docker compose up --build -d
curl.exe http://localhost:8080/actuator/health
```

Compose chuyển các biến Notion tới container API. Khi nâng cấp từ Sprint 1, giữ nguyên volume PostgreSQL: Flyway tự áp dụng V2, không sửa V1 và không cần xóa dữ liệu GitHub. Không chạy `docker compose down -v` nếu muốn giữ dữ liệu.

Chạy IDE/Maven: đặt biến môi trường trực tiếp, gồm `DB_URL=jdbc:postgresql://localhost:5433/personalhub`, `DB_USERNAME`, `DB_PASSWORD`, `PERSONALHUB_API_KEY` và các biến Notion. Spring Boot không tự đọc `.env`; Compose đọc file này để nội suy cấu hình. Container API kết nối `db:5432`, còn cổng DB trên máy là `5433`.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| NOTION_ENABLED | false | Bật module; khi tắt, endpoints trả 503 |
| NOTION_API_VERSION | 2025-09-03 | Header phiên bản Notion |
| NOTION_MAX_PAGES | 100 | 1–100 trang; mỗi trang tối đa 100 kết quả |
| NOTION_OVERLAP_MINUTES | 5 | Đọc lại vùng checkpoint, cho phép 1–1440 phút |
| NOTION_SYNC_ENABLED | false | Bật scheduler độc lập GitHub |
| NOTION_SYNC_FIXED_DELAY_MS | 1800000 | Khoảng nghỉ sau khi job hoàn tất |
| NOTION_SYNC_INITIAL_DELAY_MS | 30000 | Chờ lần sync đầu tiên |

`NOTION_API_URL` dùng cho phát triển/mock khi chạy trực tiếp, mặc định `https://api.notion.com/v1`. Compose cố định endpoint mặc định. Timeout client dùng cấu hình chung `spring.http.clients`: connect 5 giây, read 20 giây.

## 3. REST API

Mọi endpoint cần header `X-API-Key` của PersonalHub; không gửi Notion token cho API này. Dùng biến môi trường để tránh ghi key thật vào ví dụ:

```powershell
$apiHeaders = @{ 'X-API-Key' = $env:PERSONALHUB_API_KEY }
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/notion/sync' -Headers $apiHeaders
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/notion/tasks?page=0&size=20' -Headers $apiHeaders
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/notion/sync-runs' -Headers $apiHeaders
```

| Method và đường dẫn | Mô tả |
| --- | --- |
| POST /api/v1/notion/sync | Sync incremental; lần đầu đọc toàn bộ |
| POST /api/v1/notion/sync?full=true | Đọc lại toàn bộ, bỏ filter checkpoint |
| GET /api/v1/notion/tasks | Danh sách JSON; page=0, size=20; size 1–100 |
| GET /api/v1/notion/tasks?status=Done&includeArchived=true | Lọc đúng tên status, cho phép bản ghi archived |
| GET /api/v1/notion/tasks/{uuid} | Task; 404 nếu không thuộc source hiện tại |
| GET /api/v1/notion/sync-runs?limit=20 | Lịch sử, limit 1–100 |
| GET /api/v1/notion/sync-runs/{uuid} | Chi tiết một lần sync |

Sync Notion thực hiện **đồng bộ trong request**: 200 với run `SUCCESS`; khi run `FAILED`, trả 502 dạng Problem Details kèm `syncRunId` để tra `GET /api/v1/notion/sync-runs/{id}`. Trả 409 khi source đang sync; không trả 202. Với nguồn lớn, tăng timeout HTTP/proxy hoặc sử dụng scheduler rồi xem lịch sử. HTTP 400 cho pagination không hợp lệ, 401 khi thiếu/sai API key, 503 khi Notion tắt. Các lỗi dùng `application/problem+json`; xem [hướng dẫn API](API_GUIDE_VI.md).

Run trả `id`, `dataSourceId`, `status`, `startedAt`, `finishedAt`, `tasksProcessed`, `errorMessage`. Danh sách task là snapshot đã cache, không gọi Notion khi đọc. Tất cả truy vấn được giới hạn theo source hiện cấu hình.

## 4. Database và cơ chế sync

- V2 tạo `notion_tasks`, `notion_sync_states`, `notion_sync_runs`; không thay đổi bảng GitHub.
- Repository Notion dùng Spring JDBC trên cùng DataSource và transaction manager do Spring Data JPA cấu hình. PostgreSQL `ON CONFLICT` upsert theo page UUID; không ghi đè dữ liệu mới bằng payload cũ.
- Giữ nguyên text date/time của Notion để không làm lệch ngày; timestamp hệ thống là TIMESTAMPTZ.
- Mỗi lần sync có run riêng. Giao dịch chính giữ PostgreSQL advisory transaction lock theo source, nên các instance cùng database không sync chồng source. Khóa tự nhả khi commit, rollback hoặc kết nối bị đóng.
- Lấy watermark sau khi có khóa; lần sau query `last_edited_time on_or_after (last_success_at - overlap)`. Chỉ cập nhật checkpoint sau khi tải hết các trang và lưu thành công.
- Tasks, checkpoint và trạng thái SUCCESS commit cùng nhau. Khi thất bại, rollback toàn bộ và lưu FAILED trong giao dịch riêng. Lỗi không lưu raw response/token. Startup và lượt sync mới phục hồi các run RUNNING bị gián đoạn của source hiện tại sau khi lấy được khóa; không sửa worker đang giữ khóa hoặc lịch sử đã kết thúc. RUNNING mới chỉ được ghi khi đã có khóa. Xem [recovery Notion và yêu cầu pool kết nối](NOTION_RECOVERY_VI.md).
- Retry tối đa 2 lần cho HTTP 429/5xx. Tôn trọng Retry-After dạng số giây; nếu yêu cầu chờ quá 60 giây thì kết thúc job để thử lại lần sau. Các lỗi 4xx khác không retry. [Giới hạn và Retry-After của Notion](https://developers.notion.com/reference/request-limits).
- Cursor thiếu/lặp hoặc vượt max-pages gây FAILED, không coi dữ liệu chưa tải hết là sync thành công.
- Response thiếu `results`/`has_more`, hoặc có `request_status.type=incomplete`, cũng gây FAILED và giữ checkpoint. Notion có thể cắt query ở 10.000 kết quả rồi trả `has_more=false`; không được xem riêng cờ đó là bằng chứng đã tải đủ. Nếu gặp giới hạn này, chia nhỏ data source; tăng NOTION_MAX_PAGES không vượt được giới hạn phía Notion. Xem [giới hạn query chính thức](https://developers.notion.com/reference/query-a-data-source#pagination-limit).

Giới hạn Sprint này: một data source cho mỗi cấu hình triển khai; chỉ đọc page properties, chưa đọc block content hay ghi ngược về Notion. Query có thể không trả page đã xóa, chuyển source hoặc mất quyền; module không suy luận xóa từ sự vắng mặt, nên bản cache cũ có thể còn. `full=true` đọc lại các page đang thấy được, không bảo đảm đối soát xóa. Cần webhook hoặc truy vấn từng page để theo dõi đầy đủ thao tác xóa. Nếu payload trả archived/in_trash thì lưu cờ và ẩn khỏi danh sách mặc định. Vùng overlap giảm rủi ro chậm xuất hiện; có thể chạy full định kỳ thủ công nếu cần.

## 5. Kiểm thử

```powershell
mvn -B test
mvn -B verify
```

`test` chạy unit tests GitHub và Notion. `verify` thêm integration tests PostgreSQL bằng Testcontainers `postgres:17-alpine`; cần Docker đang hoạt động. Integration tests kiểm tra Flyway, upsert, dữ liệu cũ, rollback task/checkpoint, advisory lock và chuỗi bảo vệ API key thật của Spring Security.

Tests Notion còn kiểm tra scheduler bật/tắt và xung đột, response thiếu trường/phân trang lặp, query bị cắt, giới hạn retry 503, UUID dạng chuẩn và luồng POST sync → GET task/lịch sử qua REST với PostgreSQL. Notion API được giả lập trong tests, không cần token thật.

Nếu đã có PostgreSQL dành riêng cho test, có thể chạy không cần Docker:

```powershell
$env:IT_DB_URL = 'jdbc:postgresql://127.0.0.1:55439/personalhub_test'
$env:IT_DB_USERNAME = 'personalhub_test'
$env:IT_DB_PASSWORD = '' # Đặt mật khẩu test nếu server yêu cầu.
mvn -B verify
```

Chỉ dùng database **rỗng và có thể xóa**: tests xóa bảng Notion để cô lập từng ca kiểm thử. Không trỏ IT_DB_URL vào database đang sử dụng. Test GitHub vẫn giữ nguyên assertions, chỉ dùng chung bộ chọn database test.

## 6. Secrets và xử lý lỗi

`.env.example` không chứa token/mật khẩu khả dụng; `.gitignore` loại `.env*`, ngoại trừ `.env.example`. Không copy `.env`, `.git`, `target` hay log vào gói source. Không đăng kết quả `docker compose config` đã nội suy secrets.

401 từ Notion: kiểm tra token. 403/404: quyền connection và data source ID. Lỗi title property: kiểm tra `NOTION_TITLE_PROPERTY`, cột phải là Title. Pagination vượt giới hạn: tăng NOTION_MAX_PAGES trong giới hạn, giảm phạm vi data source hoặc chia nguồn; checkpoint được giữ lại. FAILED không tự xóa tasks cũ. Lỗi cấu hình/database khác được lưu dưới thông báo chung; kiểm tra kết nối và schema mà không bật log HTTP chứa Authorization.
