# Xác minh dashboard — 04/11/2026

## Kết quả

- Java 21.0.12.1 / Maven 3.9.16 / PostgreSQL 17.11 / Spring Boot 4.1.2: **95 unit tests + 22 integration tests PASS**, 0 failures/errors/skipped.
- Node 24.12.0: **11 client tests PASS**, 0 failures/skipped/cancelled. Tổng 128 automated tests ở hai nhóm Java và client.
- **16 kiểm tra UI** qua trình duyệt với server dữ liệu giả lập riêng; đã xem ảnh desktop/mobile, keyboard và độ rộng 375/768/1024/1440px. Không có horizontal overflow ở các trang được kiểm tra.
- Build/up Docker Compose thành công. `/dashboard` và 5 asset trả 200; hash nội dung asset đang phục vụ khớp source. CSP self-only, form-action none và X-Frame-Options DENY được xác minh trên ứng dụng thật.
- Health **UP**. API tổng quan thật sau deploy khớp PostgreSQL: **3 repository, 2 commit, 6 task active, 0 archived**; latest runs SUCCESS. Không thay đổi `.env` hoặc xóa volume.
- Newman 6.2.3: **21 HTTP requests, 61 assertions, 0 failures**, 0 POST; 3 trigger sync bỏ qua do runSync=false.

## Phạm vi đã chứng minh

| Nhóm | Kết quả |
| --- | --- |
| Kết nối | Key chỉ gửi trong header; connect hiển thị số liệu; ô nhập được xóa; key sai hiện lỗi 401 và request ID |
| Phiên sử dụng | Disconnect xóa dòng dữ liệu; reload quay về màn hình khóa; request cũ bị abort và response cũ không được trả sang phiên mới |
| Mạng | Credential mode omit, cache no-store, redirect error; lỗi HTML/network không đưa raw nội dung ra UI; timeout POST không tự retry |
| GitHub | Repository và commit hiển thị; lọc repository reset trang; trang 2 hiển thị commit còn lại; job QUEUED hoàn tất qua polling và mở lại nút |
| Notion | Lọc đúng status; bật archived thay số dòng đúng; sync hiển thị số xử lý; lỗi sync hiển thị request ID và thông báo an toàn trong lịch sử |
| Dữ liệu rỗng/tắt | Empty state và pagination đúng; Notion disabled có thông báo riêng và khóa control; GitHub vẫn dùng được |
| Render an toàn | HTML trong title/repo giữ nguyên dạng text; không tạo img/script; URL javascript hoặc domain giả mạo không thành link |
| Responsive/keyboard | Không tràn ngang ở bốn độ rộng; Tab/Enter kết nối; điều hướng đưa focus về heading và trở về đầu trang; reset viewport sau QA |
| Security thực | Public shell/asset GET/HEAD; endpoint API vẫn 401 khi thiếu key; asset ngoài allowlist và POST shell bị từ chối |

Automated Java tests dùng database Docker riêng qua IT_DB_URL, được dọn sau verify; không dùng DB của người dùng. Đường tự khởi tạo Testcontainers không chạy riêng trong lượt này. Client tests dùng fetch giả lập; browser QA dùng fake key và dữ liệu giả ở localhost:18080, không dùng token thật hoặc tác động GitHub/Notion. Preview server đã được dừng.

Kiểm tra trên ứng dụng thật dùng HTTP GET cho shell/asset/API và đối chiếu DB; không nhập key thật vào browser QA hoặc trigger sync trên nguồn thật trong mốc này. Các màn hình/auth/sync giả lập và các kiểm tra HTTP ứng dụng thật được báo tách biệt. Không có load test hoặc kiểm tra trên trình duyệt Safari/Firefox riêng. CSS có reduced-motion; chưa giả lập riêng setting này qua browser QA.

## Bàn giao

[Hướng dẫn sử dụng](DASHBOARD_UI_VI.md) · [Bằng chứng](dashboard-ui-evidence.json) · [Ảnh desktop](assets/dashboard-desktop.png) · [Ảnh mobile](assets/dashboard-mobile.png)

Source: `dist/personalhub-api-dashboard-source-2026-11-04.zip`, kèm SHA-256. Gói gồm UI, backend, test, OpenAPI/Postman, ảnh giả lập và docs; loại `.env`, key/token thật, `.git`, workspace Postman cá nhân, local environment, cache/logs. Không stage/commit/push.

Không thêm dependency runtime hoặc migration. Giao diện native HTML/CSS/ES modules đi cùng Spring Boot thay cho một dịch vụ frontend tách riêng; Node chỉ dùng trong test. API contract giữ 14 thao tác, Postman 24 request. CI bổ sung Node client tests, chưa chạy GitHub Actions từ repo remote.

Các hạng mục tiếp theo: backup/restore PostgreSQL, triển khai HTTPS và metrics sync có lưu lịch sử. Toàn hệ thống vẫn dùng một API instance/database và cơ chế single-user API key.
