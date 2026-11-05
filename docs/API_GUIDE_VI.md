# Sử dụng API bằng OpenAPI và Postman

PersonalHub dùng header `X-API-Key` cho `/api/v1/**`. Giá trị là `PERSONALHUB_API_KEY` trong `.env` của backend. GitHub/Notion token chỉ đặt ở backend.

## 1. Mở tài liệu OpenAPI

File chuẩn OpenAPI 3.1 nằm tại `src/main/resources/openapi/personalhub-openapi.json`. Có thể import trực tiếp file này vào Postman hoặc công cụ hỗ trợ OpenAPI 3.1.

Backend cũng phục vụ cùng file tại **GET `/api/v1/openapi.json`**, yêu cầu `X-API-Key`. Đây là tài liệu JSON; không có Swagger UI trong bản này. Mở URL trực tiếp trong trình duyệt mà không gửi header sẽ trả 401.

Tài liệu bao phủ 14 thao tác, gồm health, chính endpoint tài liệu, dashboard overview, GitHub, lịch sử sync và Notion. Nó mô tả tham số, giới hạn pagination, schemas, request ID và response lỗi. Unit tests kiểm tra các tham chiếu; integration test đối chiếu danh sách thao tác với các controller thực tế.

## 2. Import Postman

1. Mở Postman, chọn **Import**, chọn `postman/PersonalHub.postman_collection.json`.
2. Import thêm `postman/PersonalHub.local.example.postman_environment.json`.
3. Chọn environment `PersonalHub local (example)`.
4. Đặt `baseUrl` thành `http://localhost:8080` và điền `apiKey` bằng khóa trong `.env`. Giữ giá trị này ở môi trường cá nhân trên máy.
5. Gửi **Read API → Health (public)**, sau đó **OpenAPI contract**.
6. Chạy các request đọc dữ liệu GitHub/Notion. Folder Notion yêu cầu backend đã cấu hình `NOTION_ENABLED=true`, token và data source hợp lệ.

Collection có 24 request trong ba nhóm:

| Nhóm | Công dụng |
| --- | --- |
| Read API | Health, OpenAPI, dashboard overview, request ID echo, repositories, commits, stats, tasks, lịch sử và chi tiết run |
| Sync (opt in) | Trigger GitHub, incremental Notion và full Notion |
| Error responses | Thiếu/sai key, số ngày overview sai, size sai, UUID sai, task không tồn tại, method sai, endpoint bị cấm |

Các request danh sách tự lưu ID đầu tiên vào collection variables để request chi tiết dùng lại. Nếu danh sách rỗng, request chi tiết được bỏ qua. Bạn cũng có thể tự điền `githubRunId`, `notionRunId` hoặc `notionTaskId`.

### Chạy sync

`runSync=false` là mặc định. Các request trong **Sync (opt in)** bị bỏ qua cả khi bấm Send và khi chạy collection. Để gửi chúng, đổi **biến `runSync` trong environment đang chọn** thành chuỗi `true`; sau kiểm tra đặt lại `false`.

- GitHub trả 202, kèm `Location` và ID. Dùng request chi tiết run để xem đến khi SUCCESS hoặc FAILED.
- Notion chạy trong request: 200 khi SUCCESS, 409 nếu đang sync cùng source, 502 nếu job FAILED và 503 nếu module tắt.
- Full Notion đọc lại các page đang truy cập được; không tự đối soát page bị xóa hoặc mất quyền.

Các scripts Postman kiểm tra HTTP status, request ID và cấu trúc Problem Details. Khi chạy toàn bộ collection với `runSync=false`, ba request sync được bỏ qua. Để toàn bộ nhóm Notion và trường hợp task không tồn tại đạt kỳ vọng, cần bật Notion trước. Overview vẫn chạy được khi Notion tắt; xem [hướng dẫn tổng quan và tra log](DASHBOARD_OVERVIEW_VI.md).

## 3. Response lỗi thống nhất

Lỗi từ Spring Security và Spring MVC dùng **`application/problem+json`** theo [RFC 9457 được Spring hỗ trợ](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

Ví dụ thiếu API key:

```json
{
  "title": "Unauthorized",
  "status": 401,
  "detail": "Missing or invalid X-API-Key.",
  "instance": "/api/v1/github/repositories",
  "code": "UNAUTHORIZED",
  "requestId": "a0000000-0000-0000-0000-000000000001"
}
```

`title`, `status`, `detail`, `instance` và `code` có trong các lỗi do ứng dụng xử lý. Sau security filter, `requestId` khớp header `X-Request-ID` để tra log; UUID trong ví dụ chỉ minh họa. `type` có giá trị mặc định `about:blank`; Spring có thể bỏ trường này khi serialize. `instance` là đường dẫn, không chứa query string.

| HTTP | code | Cách xử lý |
| --- | --- | --- |
| 400 | INVALID_REQUEST | Kiểm tra kiểu tham số, UUID và giới hạn pagination |
| 401 | UNAUTHORIZED | Gửi đúng header X-API-Key |
| 403 | FORBIDDEN | Endpoint không được cấu hình cho phép truy cập |
| 404 | NOT_FOUND | ID hoặc đường dẫn không tồn tại trong phạm vi nguồn hiện tại |
| 405 | METHOD_NOT_ALLOWED | Dùng đúng GET/POST; header Allow chỉ ra method được hỗ trợ |
| 406 | NOT_ACCEPTABLE | Chọn định dạng response JSON phù hợp |
| 409 | SYNC_CONFLICT | Chờ job đang chạy hoàn tất |
| 415 | UNSUPPORTED_MEDIA_TYPE | Kiểm tra Content-Type của request |
| 500 | INTERNAL_ERROR | Kiểm tra dịch vụ/database; response không chứa nội dung exception |
| 502 | UPSTREAM_ERROR | Đọc syncRunId rồi tra run Notion để xem lỗi an toàn đã lưu |
| 503 | INTEGRATION_UNAVAILABLE | Kiểm tra cấu hình bật Notion |

Health do Actuator cung cấp có định dạng riêng: khi database DOWN, health có thể trả 503 với JSON health thay vì Problem Details. Các lỗi bị chặn trước ứng dụng, như proxy hoặc HTTP request không hợp lệ ở Tomcat, có thể có định dạng khác.

### Khi Notion sync trả 502

```json
{
  "title": "Bad Gateway",
  "status": 502,
  "detail": "Sync failed; inspect the referenced sync run for details.",
  "instance": "/api/v1/notion/sync",
  "code": "UPSTREAM_ERROR",
  "requestId": "a0000000-0000-0000-0000-000000000001",
  "syncRunId": "00000000-0000-0000-0000-000000000001"
}
```

UUID trên chỉ minh họa. Lấy `syncRunId` thật và gọi **GET `/api/v1/notion/sync-runs/{id}`**. Endpoint lịch sử vẫn trả HTTP 200 với run `status=FAILED` và `errorMessage` an toàn. Không suy luận lỗi này là lỗi cú pháp PowerShell hoặc sai mật khẩu PostgreSQL chỉ từ mã 502.

## 4. Thay đổi tương thích

- Response thành công và header xác thực giữ nguyên.
- Có thêm header X-Request-ID và trường requestId trong lỗi xử lý sau filter. Client có thể bỏ qua trường mới; muốn tra log thì lưu ID này cùng lỗi. Đây là thay đổi bổ sung, không thay thế syncRunId.
- 401 trước đây trả `{ "error": "UNAUTHORIZED" }`; client giờ đọc `code` và `status` của Problem Details.
- 502 của POST Notion trước đây trả trực tiếp NotionSyncRun. Client giờ đọc `syncRunId`, rồi lấy chi tiết tại endpoint lịch sử. Trường `status` trong lỗi HTTP là số 502; `status` trong một sync run là chuỗi như FAILED.
- 400/404/409/503 từ controller và lỗi tham số được chuẩn hóa cùng định dạng. Lỗi 500 không phản hồi raw exception, SQL hoặc token.

## 5. Cấu hình và bàn giao

Không commit `.env` hoặc file environment đã điền secret. File environment mẫu và collection có `apiKey` rỗng. Nếu cần lưu bản export cục bộ, dùng tên như `postman/PersonalHub.local.postman_environment.json`, đã được `.gitignore` loại ra; không đưa file đó vào ZIP source.

Giữ Spring Boot 4.1.2, Java 21, PostgreSQL và Flyway; không thêm dependency OpenAPI vào POM. Contract là file được duy trì cùng controller và được kiểm tra khi chạy `mvn verify`.
