# Xác minh OpenAPI, Postman và response lỗi — 03/11/2026

## Kết quả

- **49 unit tests + 10 integration tests PASS**, 0 failures/errors/skipped.
- Build và `mvn verify` trên **Java 21.0.12.1**, Maven 3.9.16, **PostgreSQL 17.11** trong Docker.
- OpenAPI 3.1 và Postman collection 2.1 đều hợp lệ theo schema chính thức.
- Contract mô tả **13 thao tác**; integration test đối chiếu đủ các controller `/api/v1/**` thực tế.
- Postman có **21 request**. Newman 6.2.3 chạy trên API local đã cập nhật: **17 HTTP requests, 32 assertions, 0 failures**. Bỏ qua 3 request sync vì `runSync=false` và 1 request chi tiết vì lịch sử GitHub rỗng. Không gửi POST trong lượt Newman này.
- `docker compose up -d --build api` thành công; API local đang chạy, health UP. Volume database và cấu hình Notion được giữ lại.

## Đã kiểm tra gì

Unit tests kiểm tra JSON/ref của OpenAPI, coverage của Postman, file mẫu không có API key, default runSync=false, validation tham số, UUID, 404, 405 với Allow, conflict GitHub/Notion, Notion disabled và lỗi 500 được che nội dung.

Integration tests kiểm tra chuỗi Spring Security thật và MVC thật: 401/403 cùng Problem Details, endpoint contract được bảo vệ bằng API key, route không tồn tại trả 404, Notion tắt trả 503. Test Notion upstream lỗi xác minh 502 có `syncRunId`, run FAILED vẫn đọc được với thông báo an toàn, checkpoint và cache giữ nguyên.

Các tests GitHub persistence, Notion upsert/rollback/advisory lock và REST thành công tiếp tục PASS. Database automated tests là container riêng, không publish cổng, dùng dữ liệu test và được xóa cùng network sau khi xong. Bộ chọn database dùng IT_DB_URL để kết nối container này; không chạy riêng đường tự khởi tạo container của Testcontainers trong lần verify này.

Newman dùng API key từ Compose trong bộ nhớ, không ghi environment đã có secret ra file, không in nội dung task. Nó xác minh request/response thực trên Docker API, bao gồm lỗi thiếu/sai key, pagination, UUID, 404, 405 và 403.

## Thay đổi cần biết khi nâng cấp client

401 và các lỗi MVC/Security dùng `application/problem+json`. POST Notion sync thất bại trả Problem Details có `syncRunId`, thay vì trả trực tiếp NotionSyncRun. Dùng ID này để đọc `/api/v1/notion/sync-runs/{id}`. Response thành công giữ nguyên.

`type=about:blank` là mặc định RFC 9457 và có thể bị Spring lược khỏi JSON. Các trường bắt buộc trong contract là `title`, `status`, `detail`, `instance`, `code`. Không phản hồi raw exception hoặc rejected parameter value trong detail của lỗi chuyển kiểu.

OpenAPI là file contract được duy trì cùng source; chưa có Swagger UI. Thêm/sửa endpoint phải cập nhật file và Postman, rồi chạy `mvn verify`. Không thêm dependency vào POM trong mốc này.

## Chạy lại và sử dụng

Trong môi trường có JDK 21 và Docker:

```text
mvn -B test
mvn -B verify
```

CI hiện có tiếp tục chạy hai lệnh này. File kết quả của lượt Java 21 trên máy nằm ở `target/api-java21/surefire-reports` và `target/api-java21/failsafe-reports`. Dữ liệu chi tiết được loại khỏi source ZIP.

Xem [hướng dẫn import và sử dụng](API_GUIDE_VI.md), [bằng chứng đã loại secrets](api-contract-evidence.json). File collection và environment mẫu nằm trong `postman/`; API key mẫu rỗng. Gói source mới chứa OpenAPI, Postman và hướng dẫn, loại `.env`, Git history, cấu hình workspace Postman cá nhân và build cache.
