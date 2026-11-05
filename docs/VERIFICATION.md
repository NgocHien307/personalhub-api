# Báo cáo xác minh PersonalHub API — 04/11/2026

## Kết luận hiện tại

Source có GitHub MVP, Notion Integration, recovery GitHub/Notion, API tổng quan, Request ID và giao diện dashboard. Build `verify` mới nhất thành công trên **Java 21.0.12.1, Maven 3.9.16, PostgreSQL 17.11**, Spring Boot 4.1.2: **95 unit tests + 22 integration tests**, 0 failures/errors/skipped. **11 client tests** trên Node 24.12.0 cũng PASS.

[Xác minh mới nhất: dashboard](DASHBOARD_UI_VERIFICATION.md) · [Bằng chứng mới](dashboard-ui-evidence.json) · [Mở dashboard](DASHBOARD_UI_VI.md) · [Checklist Notion/GitHub](checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md)

Các bài kiểm tra dữ liệu thật đã hoàn tất trước mốc hiện tại:

- **GitHub: 4/4 PASS**, gồm tạo commit trên repo public của owner, kiểm tra dữ liệu trong PostgreSQL và sync lặp. [Báo cáo GitHub](GITHUB_MVP_VERIFICATION.md).
- **Notion: 6/6 PASS**, gồm sync, đối chiếu các trường, chống trùng, scheduler tự khởi tạo job, sửa bản ghi giữ ID, tạo mới 5 → 6 và full replay giữ 6 → 6. [Báo cáo Notion](NOTION_LIVE_VERIFICATION.md).

## Thay đổi trong mốc hiện tại

- Giao diện tại `/dashboard`, phục vụ trong cùng JAR/Compose. Có 4 phần Tổng quan, GitHub, Notion, Đồng bộ; biểu đồ status, lọc/phân trang, polling GitHub, đồng bộ Notion, trạng thái rỗng/disabled/lỗi.
- Public shell/assets GET/HEAD theo allowlist; API dữ liệu tiếp tục yêu cầu key. Key chỉ trong bộ nhớ tab; dữ liệu nguồn dùng textContent, link ngoài được kiểm tra. CSP self-only không dùng unsafe-inline.
- Bổ sung test client và job CI Node; 16 kiểm tra UI với dữ liệu giả lập, 4 độ rộng và keyboard. Ảnh bàn giao không chứa dữ liệu cá nhân.
- Checklist có thêm PH-U01–PH-U04, hiện 18 đầu việc thực hành chưa có kết quả người dùng được ghi nhận.

Các mốc trước tiếp tục được kiểm tra trong toàn bộ suite:

- GET `/api/v1/dashboard/overview?days=30` đọc cache GitHub/Notion và run mới nhất trong transaction read-only REPEATABLE READ. Notion tắt vẫn trả 200 với enabled=false.
- X-Request-ID cho request đi qua security filter; lỗi Security/MVC có thêm requestId. Log dùng method/route pattern/status/duration, không log raw URL/query, auth headers hoặc body; MDC được dọn sau request.
- OpenAPI/Postman bao phủ endpoint và Request ID; PH-O01/PH-O02 dùng để kiểm tra API tổng quan/log.

- Notion phục hồi RUNNING bị gián đoạn ở startup và trước lượt sync mới, chỉ khi lấy được khóa của source; giữ run đang hoạt động và lịch sử kết thúc.
- Notion ghi RUNNING sau khi có khóa, commit riêng lịch sử/recovery; task/checkpoint/SUCCESS vẫn atomic. Thất bại rollback dữ liệu và ghi FAILED riêng.
- Có checklist Markdown/CSV và file commit thử cho lượt kiểm tra thực hành mới. Các checkbox thực hành chưa được đánh dấu PASS.

Các thay đổi GitHub đã hoàn tất ở mốc trước và tiếp tục được kiểm tra trong toàn bộ suite:

- GitHub GET retry tối đa 3 lần/trang với 5xx, network/timeout, 429 hoặc 403 có rate-limit headers. Tôn trọng Retry-After/reset, dừng thay vì gọi sớm nếu yêu cầu chờ quá 60 giây.
- Startup coordinator chuyển GitHub run QUEUED/RUNNING bị gián đoạn sang FAILED trước khi nhận trigger. Giữ lịch sử kết thúc, dữ liệu repository/commit và checkpoint. Không tự chạy lại job cũ.
- Job bị executor từ chối nhận được ghi FAILED; các nhánh lỗi giải phóng busy guard.
- REST client/worker dùng thông báo lỗi an toàn, tránh lưu/log raw upstream body hoặc arbitrary exception message.

- Giữ Spring Boot 4.1.2, Java target 21, PostgreSQL, Flyway và POM hiện có.
- OpenAPI mô tả 14 thao tác, phục vụ tại GET `/api/v1/openapi.json` có API key. Contract là file source, chưa có Swagger UI.
- Postman collection có 24 request; environment mẫu có apiKey rỗng, runSync=false. Các request sync chỉ chạy khi chủ động bật biến này.
- MVC và Spring Security dùng `application/problem+json`, với các trường title/status/detail/instance/code; type mặc định about:blank có thể được Spring bỏ khỏi JSON.
- POST Notion thất bại trả 502 có syncRunId để lấy run FAILED ở endpoint lịch sử. Đây là thay đổi định dạng lỗi; response thành công giữ nguyên.
- Migrations V1/V2 và POM giữ nguyên; Notion vẫn dùng advisory lock, atomic task/checkpoint và recovery. REST thêm endpoint overview và requestId trong lỗi. Không thêm dependency, commit hoặc push. [Báo cáo mốc API ngày 03/11](API_CONTRACT_VERIFICATION.md) ghi kết quả của mốc trước.
- `.env` thật chỉ nằm trên máy, không có trong source ZIP. Workspace Git hiện đã tồn tại nhưng các file dự án còn untracked; không có bước stage/commit tự động.

## Kết quả kiểm tra mới nhất

| Kiểm tra | Kết quả |
| --- | --- |
| Compile/package Java 21 và Spring Boot JAR | PASS |
| Unit tests | **95 passed**, 0 failures/errors/skipped |
| Integration tests | **22 passed**, 0 failures/errors/skipped |
| Node client tests | **11 passed**, 0 failures/skipped/cancelled |
| Browser UI QA | **16 checks**, dữ liệu giả; 375/768/1024/1440px, keyboard, filters, paging, sync, lỗi và render an toàn |
| Live dashboard shell/assets | 200, hash khớp source; CSP và MIME đúng |
| PostgreSQL test riêng | 17.11; Flyway V1/V2, Hibernate validate PASS |
| API contract và coverage controller thực tế | 14 thao tác, PASS |
| Schema chính thức OpenAPI 3.1/Postman 2.1 | PASS trên file contract cập nhật |
| Retry, recovery và rollback GitHub | PASS theo các tình huống trong báo cáo mới nhất |
| Recovery Notion và bảo vệ run đang giữ khóa | PASS với PostgreSQL thật và Spring context mới |
| Newman 6.2.3 trên Docker API local mới | **21 HTTP requests, 61 assertions, 0 failures** |
| Request sync trong lượt Newman | 0 POST; bỏ qua 3 trigger sync vì runSync=false |
| Docker Compose build/up | PASS, health UP |
| Overview và PostgreSQL | 200; khớp 3 repository, 2 commit, 6 task active, 0 archived |
| Request ID trên API local | Echo/UUID replacement/log đúng một lần, lỗi 400/401 body/header khớp |
| GitHub thật ở mốc trước 04/11 | 2 lượt 202 → QUEUED → RUNNING → SUCCESS; 3 repository/lượt; xem báo cáo GitHub reliability |
| Notion thật ở mốc recovery trước | Full sync 200/SUCCESS, xử lý 6 task; GET lịch sử xác nhận SUCCESS |
| Số repository/commit khi nâng cấp Notion | 3/2 → 3/2, giữ nguyên |
| Số task Notion trước/sau | 6 → 6 |
| Security/MVC errors | 400/401/403/404/405/409/500/502/503 được kiểm tra theo các tình huống tương ứng |

Automated tests dùng database Docker riêng qua IT_DB_URL, không dùng database ứng dụng hiện có. Container test và network được xóa sau khi xong. Đường tự khởi tạo container của Testcontainers không được chạy riêng trong lần này. Retry/rate limit dùng HTTP giả lập; recovery GitHub/Notion dùng dữ liệu bị gián đoạn đã seed và callback khởi tạo bean trong Spring context mới với PostgreSQL thật. Overview tests xác minh cache/scope/status và không gọi upstream. Các lượt live hiện tại chỉ GET; Notion full sync và hai lượt GitHub thuộc mốc trước. Scheduler hiện có giữ cấu hình.

Các mốc trước lần lượt có 38/7, 49/10, 80/12, 83/15 và 95/19 unit/integration tests. Dashboard thêm 3 integration tests và 11 test client; Java suite hiện 95/22. UI QA dùng fake key và fake upstream tại localhost:18080; không nhập token thật vào browser QA, preview server đã được dừng. Live HTTP kiểm tra riêng trên ứng dụng thật chỉ đọc shell/asset/API.

## Chạy lại

Dùng JDK 21 và Docker đang hoạt động:

```text
mvn -B test
mvn -B verify
node --test src/test/frontend/dashboard-core.test.mjs
```

Hoặc trỏ IT_DB_URL/IT_DB_USERNAME/IT_DB_PASSWORD tới database test rỗng, có thể xóa. Không trỏ vào DB đang sử dụng. Báo cáo đầy đủ của lượt Java 21 trên máy ở `target/api-java21/surefire-reports` và `target/api-java21/failsafe-reports`, không nằm trong ZIP vì có logs/config test.

Dockerfile và CI tiếp tục dùng Java 21; CI hiện có sẽ chạy tests mới khi source được đưa lên GitHub. Chưa có bước push hoặc xác minh một lượt GitHub Actions mới trong mốc này.

## Bàn giao

Gói source cập nhật: `dist/personalhub-api-dashboard-source-2026-11-04.zip`, kèm SHA-256. Gói có UI/backend/test, checklist, OpenAPI/Postman, docs và ảnh dùng dữ liệu giả lập; loại `.env`, `.git`, workspace Postman cá nhân, file environment local, build cache và logs. Các gói mốc trước được giữ riêng.

Giải nén, copy `.env.example` sang `.env`, điền cấu hình riêng, chạy Docker Compose và mở [dashboard](DASHBOARD_UI_VI.md). Giới hạn còn lại: một API instance/database, single-user API key, chưa đối soát xóa Notion hoặc OAuth; chưa triển khai HTTPS/backup/metrics lịch sử. Notion cần pool ít nhất 2 kết nối cho transaction lịch sử riêng; mặc định đáp ứng. Xem README và docs để biết phạm vi từng module.
