# Xác minh recovery Notion và checklist — 04/11/2026

## Kết quả

- `mvn verify` BUILD SUCCESS trên **Java 21.0.12.1, Maven 3.9.16, PostgreSQL 17.11**, Spring Boot 4.1.2.
- **83 unit tests + 15 integration tests PASS**, 0 failures/errors/skipped. Thêm 3 unit + 3 integration tests so với mốc GitHub trước đó.
- `docker compose up -d --build api` thành công, API local health **UP**.
- Notion thật trên API mới: full sync HTTP **200**, run **SUCCESS**, xử lý **6 task**, đọc lại lịch sử xác nhận SUCCESS. Database vẫn **6 task, 3 repository, 2 commit**, không có run đang hoạt động tại thời điểm đối chiếu.
- Newman 6.2.3: **18 HTTP requests, 33 assertions, 0 failures**, 0 POST trong lượt collection. Một POST full sync Notion được chạy riêng như mô tả trên.

## Đã chứng minh

| Trường hợp | Kết quả |
| --- | --- |
| Startup khi Notion tắt | Không mở transaction, không truy cập repository hoặc upstream |
| Startup có run gián đoạn | Chỉ RUNNING của source hiện tại đổi thành FAILED, có finishedAt và thông báo an toàn |
| Lịch sử và dữ liệu khác | SUCCESS/FAILED cũ, source khác, GitHub, task và checkpoint giữ nguyên |
| Recovery nhiều lần | Không sửa lại run đã được đánh dấu FAILED |
| Khóa đang được giữ | Startup bỏ qua; run đang giữ khóa không bị đánh dấu thất bại |
| Sau khi khóa được nhả | Lượt sync mới phục hồi run cũ và chạy SUCCESS |
| Run mới đang gọi upstream | RUNNING nhìn thấy được trong DB; recovery từ Spring context khác không sửa run này |
| Sync mới lỗi sau recovery | Recovery cũ vẫn đã commit; run mới FAILED, task/checkpoint giữ nguyên |
| Ghi lịch sử | Lấy khóa trước khi ghi RUNNING; conflict ghi CONFLICT riêng, không tạo RUNNING trước khóa |

Tests dùng PostgreSQL Docker riêng qua IT_DB_URL, không dùng database ứng dụng. Upstream được mock; các trường hợp recovery dùng dữ liệu RUNNING đã seed và callback khởi tạo service trong Spring context mới. Container/network test được xóa sau khi hoàn tất. Không cố kill tiến trình sync của người dùng. Đường tự khởi tạo container của Testcontainers không chạy riêng trong lượt này.

Notion/GitHub REST contract, migrations V1/V2, POM và cấu hình `.env` không đổi. Không ghi dữ liệu lên Notion/GitHub trong lượt kiểm tra của agent; full sync chỉ đọc Notion và cập nhật cache local. Không stage, commit, push hoặc tạo Issue bên ngoài.

## Checklist bàn giao

- [Markdown để copy vào Notion hoặc mô tả GitHub Issue](checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md): 12 bước chuẩn bị, Notion tạo/sửa/replay/scheduler, GitHub commit/replay, xác thực, restart và bằng chứng.
- [CSV đầu việc](checklists/PersonalHub.tasks.notion.csv): 12 hàng, cột Log title và Date; Status chọn theo cấu hình workspace.
- [File nội dung commit thử](checklists/github-live-test.sample.md): dùng trên repo public đang sync ở default branch; không có secret.

Checklist này là một lượt thực hành mới của người dùng, **chưa có kết quả được ghi nhận**. Các checkbox kiểm tra thực tế vẫn để trống. Automated tests và một lượt Notion sync của agent ở trên không được dùng để đánh dấu thay các bước tạo/sửa mẫu hoặc commit của người dùng.

Module GitHub đồng bộ repository/commit, nên việc tick Issue hoặc sửa mô tả Issue chỉ phục vụ quản lý công việc. Notion phải thêm bản ghi mẫu vào đúng bảng/data source đang kết nối; nhập CSV thành bảng mới không tự thay đổi nguồn mà API đọc.

## Bàn giao và giới hạn

[Cơ chế recovery và vận hành](NOTION_RECOVERY_VI.md) · [Bằng chứng đã lược thông tin riêng tư](notion-recovery-evidence.json)

Source: `dist/personalhub-api-notion-recovery-source-2026-11-04.zip`, kèm SHA-256. Loại `.env`, token/key thật, Git history, workspace Postman cá nhân, build cache và logs. Mốc GitHub trước vẫn có ZIP và bằng chứng riêng.

Mỗi worker Notion cần ít nhất 2 kết nối DB do transaction lịch sử riêng; Hikari mặc định 10 kết nối hiện có đáp ứng. PersonalHub tổng thể vẫn giới hạn một API instance/database do guard GitHub. Chưa có vòng quét recovery riêng; recovery Notion chạy ở startup/lượt sync mới khi có khóa và database ghi được. Khi nâng cấp, dừng bản cũ trước khi khởi động bản mới.

Chạy lại với JDK 21 và Docker: `mvn -B test`, `mvn -B verify`. Reports trên máy nằm trong `target/api-java21/surefire-reports` và `target/api-java21/failsafe-reports`.
