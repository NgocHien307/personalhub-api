# Phát hành PersonalHub 0.1.0

Phiên bản ứng dụng lấy từ `pom.xml`: **0.1.0**; giữ Spring Boot 4.1.2 và Java 21. Dockerfile đóng gói JAR cùng phiên bản. Release đầu dùng tag `v0.1.0`, không thay đổi version framework hoặc database migration.

## Cổng kiểm tra trước release

- Java 21: `mvn -B verify` với database Testcontainers riêng.
- Node 24: `node --test src/test/frontend/dashboard-core.test.mjs`.
- Python 3.11+ và Docker: `python scripts/verify_operations.py` chứng minh backup, restore và các chốt từ chối ghi đè.
- `python scripts/package_source.py`: đóng gói theo danh sách cho phép, kiểm hash nội dung ZIP và quét credential-pattern/secret từ `.env` local nếu có.
- GitHub Actions `PersonalHub CI`: tất cả job trên commit đích phải thành công trước khi gắn tag. CI không dùng Notion/GitHub token thật và không xuất bản backup.

`scripts/package_source.py` tạo `dist/personalhub-api-0.1.0-source.zip` cùng file SHA-256. Gói có source, tests, OpenAPI/Postman mẫu, scripts, cấu hình production và tài liệu. Không chứa `.env`, `.git`, `.postman`, backup, log, JAR, cache hoặc credential. Dùng danh sách file trong ZIP để chuẩn bị commit; trước push kiểm `git diff --cached --stat` và đảm bảo file riêng không có trong index. Công cụ quét hỗ trợ định dạng credential phổ biến và giá trị hiện có trong `.env`, không thay thế việc xem nội dung thay đổi.

## Công bố

Giữ lịch sử repository hiện có, cập nhật qua nhánh `codex/`, chờ CI, đưa commit đã kiểm thử vào main. Gắn annotated tag `v0.1.0` vào commit được kiểm tra, push tag và tạo GitHub Release kèm source ZIP/SHA-256. Không có bước tự xuất bản khi chỉ push nhánh; release được tạo có chủ đích sau khi CI thành công. Dùng body-file cho release notes để giữ đúng nội dung và xuống dòng.

## Nội dung bản 0.1.0

- Xem repository/commit GitHub và task Notion trong dashboard; lọc, phân trang, sync thủ công và xem run history.
- Retry có giới hạn, phục hồi run bị gián đoạn, API key và request ID giúp vận hành.
- PostgreSQL backup/restore có checksum và bài kiểm tra vào DB riêng; cấu hình Caddy cho server HTTPS và hướng dẫn phục hồi.

Phạm vi: single-user, một API instance/database. Chưa có OAuth, đối soát page Notion đã xóa hoặc metrics lịch sử. Server công khai và tên miền phải được cung cấp riêng; bộ kiểm thử HTTPS local không xác nhận đã triển khai Internet.
