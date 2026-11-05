# Xác minh độ ổn định GitHub sync — 04/11/2026

## Kết quả

- **80 unit tests + 12 integration tests PASS**, 0 failures/errors/skipped. `mvn -B -ntp -o verify` BUILD SUCCESS trên **Java 21.0.12.1, Maven 3.9.16, PostgreSQL 17.11**, Spring Boot 4.1.2.
- Build Docker Compose và khởi động API local thành công; health **UP**.
- Hai lượt sync GitHub thật trên API vừa nâng cấp đều trả **202**, quan sát được **QUEUED → RUNNING → SUCCESS** và xử lý 3 repository. Mỗi lượt fetched 0 commit vì không có commit trong cửa sổ incremental; dữ liệu đã lưu giữ **2 → 2 → 2 commit**.
- PostgreSQL trước/sau vẫn có **3 repository, 2 commit, 6 task Notion**. Không có GitHub job đang hoạt động sau khi kết thúc. Không tạo hoặc sửa commit trên GitHub trong mốc này.
- Newman 6.2.3 trên API vừa nâng cấp: **18 HTTP requests, 33 assertions, 0 failures**. `runSync=false`, 0 POST trong lượt Newman; 3 trigger sync được bỏ qua. Hai POST GitHub ở trên thuộc lượt kiểm tra thủ công riêng.

## Các tình huống mới đã chứng minh

| Nhóm | Bằng chứng |
| --- | --- |
| Retry HTTP | 5xx, 429 và 403 có hướng dẫn rate limit; không retry lỗi quyền/validation thông thường; tối đa 3 lần gọi |
| Thời gian chờ | Backoff 1/2 giây; Retry-After số giây/HTTP date; reset quota; chờ tối thiểu 60 giây khi rate limit không có hướng dẫn; dừng khi vượt trần |
| An toàn khi retry | Chỉ gọi lại trang lỗi, không nhân đôi kết quả trang trước; commit endpoint vẫn hỗ trợ repo rỗng; interrupt dừng retry |
| Lỗi an toàn | Không truyền raw body/cause ra lỗi client; worker không lưu/log arbitrary exception text; lỗi ghi trạng thái vẫn giải phóng busy guard |
| Hàng đợi | Executor từ chối job thì đánh dấu FAILED; lỗi database hoặc lỗi ghi FAILED không khóa vĩnh viễn trigger tiếp theo |
| Recovery | Callback khởi tạo coordinator chuyển đúng QUEUED/RUNNING sang FAILED trong PostgreSQL; giữ run đã kết thúc, thời gian, số lượng và lỗi cũ; gọi lại recovery không sửa lịch sử |
| Dữ liệu | Recovery không sửa bảng Notion/commit/checkpoint; transaction GitHub lỗi rollback cả metadata repository và checkpoint |
| Sync tiếp theo | Worker tiếp tục dùng cửa sổ ban đầu/checkpoint-overlap; giữ số repository hoàn tất khi lỗi giữa chừng; có thể tạo job mới sau recovery |

Unit tests mới gồm 23 trường hợp retry, 5 trường hợp worker và 3 trường hợp coordinator. Integration tests thêm một trường hợp startup recovery và một trường hợp rollback. Các test Notion, security và API contract trước đó đều chạy lại thành công.

Recovery được kiểm tra bằng cách tạo dữ liệu run bị gián đoạn rồi khởi tạo coordinator trong Spring context mới với repository PostgreSQL thật. Không cố ý kill tiến trình đang dùng database của người dùng. Retry/rate limit dùng HTTP giả lập và clock/sleeper giả; không cố tạo lỗi 429 trên GitHub thật. Các lượt GitHub thật chứng minh đường sync thành công và số bản ghi ổn định.

Database automated tests nằm trong container riêng, không publish cổng; kết nối qua IT_DB_URL. Container và network test được xóa sau khi hoàn tất. Đường tự khởi tạo container của Testcontainers không chạy riêng trong lần này. `.env`, API key, token và nội dung task không xuất hiện trong bằng chứng bàn giao.

## Môi trường local và giới hạn

API/database local đang dừng từ phiên trước; đã khởi động lại database hiện có rồi build/up API. Không xóa volume hoặc đổi `.env`. `SYNC_ENABLED=false` tiếp tục được giữ, sync thủ công hoạt động. Notion scheduler giữ cấu hình hiện tại.

Recovery chỉ hỗ trợ **một API instance trên mỗi database**. Mốc này chưa triển khai distributed lease, recovery Notion hay cơ chế tự cứu run khi database phục hồi trong cùng tiến trình. Xem [quy tắc vận hành](GITHUB_SYNC_RELIABILITY_VI.md).

Kết quả đã lược thông tin riêng tư nằm trong [github-sync-reliability-evidence.json](github-sync-reliability-evidence.json). Reports trên máy ở `target/api-java21/surefire-reports` và `target/api-java21/failsafe-reports`.

## Chạy lại

```text
mvn -B test
mvn -B verify
docker compose up -d --build api
```

Hai lệnh Maven cần JDK 21; `verify` cần Docker hoặc database test rỗng qua IT_DB_URL/IT_DB_USERNAME/IT_DB_PASSWORD. Không dùng database ứng dụng làm database automated tests. Trong Postman, đặt `runSync=true` trong environment để chủ động chạy Trigger GitHub sync, rồi đọc chi tiết run và dữ liệu; sau khi thử có thể đặt lại false.

Source cập nhật: `dist/personalhub-api-notion-source-2026-11-04.zip`, kèm SHA-256. Không stage, commit hay push Git trong mốc này.
