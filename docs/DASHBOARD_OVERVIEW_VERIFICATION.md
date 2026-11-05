# Xác minh API tổng quan và Request ID — 04/11/2026

## Kết quả

- **95 unit tests + 19 integration tests PASS**, 0 failures/errors/skipped. `mvn verify` BUILD SUCCESS trên Java 21.0.12.1, Maven 3.9.16, PostgreSQL 17.11, Spring Boot 4.1.2.
- Docker Compose build/up thành công; health local **UP**.
- GET `/api/v1/dashboard/overview?days=30` trả **200** và số liệu khớp query PostgreSQL độc lập: **3 repository, 2 recorded commits, 2 authored commits, 6 task chưa archived, 0 task archived**. Có 3 nhóm status, tổng số lượng nhóm bằng 6.
- Live checks xác minh header Request ID được giữ lại, xuất hiện đúng một log hoàn tất tương ứng; lỗi Security 401 và lỗi MVC 400 có requestId khớp header. ID đầu vào sai được thay bằng UUID chuẩn.
- Newman 6.2.3: **21 HTTP requests, 61 assertions, 0 failures**, 0 POST. Bỏ qua 3 trigger sync vì runSync=false. OpenAPI có **14 thao tác**, collection **24 request**; cả hai hợp lệ theo schema chính thức đã tải từ nguồn chuẩn.

## Phạm vi đã kiểm tra

| Nhóm | Bằng chứng |
| --- | --- |
| Request ID | Tạo UUID khi thiếu; giữ/chuẩn hóa UUID hợp lệ; thay ID sai/quá dài; dọn/khôi phục MDC cả khi chain lỗi |
| Log an toàn | Log route pattern, status, duration và ID; test với raw path/query/header/body có giá trị riêng tư xác nhận chúng không xuất hiện trong completion log |
| Lỗi HTTP | Correlation qua Security 401/403 và MVC 400/500; body/header cùng ID; detail lỗi tiếp tục che arbitrary exception text |
| Tổng quan GitHub | Tái sử dụng GitHub stats; commit trong/ngoài cửa sổ, author khớp và không gắn tài khoản được phân biệt |
| Tổng quan Notion | Đếm active/archived riêng; nhóm đúng status, giữ null; dữ liệu source khác không tham gia; không bị giới hạn bởi danh sách phân trang |
| Trạng thái nguồn | Notion tắt trả 200 với enabled=false; nguồn bật chưa có cache trả số 0/null; FAILED mới nhất không làm mất cache/checkpoint thành công |
| Truy vấn | Overview không gọi GitHubApiClient hoặc NotionApiClient; request GET thực tế chỉ đọc cache |
| Contract | Danh sách controller thực tế khớp OpenAPI; Postman bao phủ endpoint mới; key mẫu vẫn rỗng, sync opt-in |

So với mốc recovery Notion, thêm 12 unit tests và 4 integration tests. Các test retry/recovery/locking/persistence GitHub–Notion trước đó đều chạy lại PASS. Mốc này giữ POM, migration V1/V2, `.env` và Compose hiện có.

Tests dùng PostgreSQL container riêng qua IT_DB_URL; test data được rollback hoặc nằm trong database có thể xóa. Container/network được dọn sau verify. Không dùng database ứng dụng cho automated tests; đường tự khởi tạo container của Testcontainers không được chạy riêng trong lượt này.

Các lượt HTTP kiểm tra local sau deploy dùng GET, không gửi lệnh trigger sync. Scheduler hiện có tiếp tục hoạt động theo cấu hình. Không tạo commit/Issue/task lên nguồn bên ngoài. Bằng chứng không chứa key/token, tên task hoặc nhãn status cá nhân.

## Bàn giao

[Hướng dẫn endpoint, Request ID và tra log](DASHBOARD_OVERVIEW_VI.md) · [Bằng chứng](dashboard-overview-evidence.json) · [Checklist thực hành](checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md)

Source: `dist/personalhub-api-overview-source-2026-11-04.zip`, kèm SHA-256. Gói loại `.env`, `.git`, workspace Postman cá nhân, local environment, target/cache và logs. Chưa stage, commit hoặc push Git.

Chạy lại: `mvn -B test`, `mvn -B verify` với JDK 21 và Docker. Reports trên máy ở `target/api-java21/surefire-reports` và `target/api-java21/failsafe-reports`. Các schema đã kiểm tra là OpenAPI 3.1 `https://spec.openapis.org/oas/3.1/schema/2022-10-07` và Postman collection 2.1 `https://schema.postman.com/collection/json/v2.1.0/draft-07/collection.json`.

Frontend và metrics sync có lưu lịch sử vẫn là backlog. Request ID hiện theo phạm vi HTTP; background worker/scheduler dùng run ID, chưa có distributed tracing. Snapshot tổng quan là cache đã lưu, không bảo đảm dữ liệu upstream vừa thay đổi đã được sync. Toàn ứng dụng tiếp tục dùng một API instance/database.
