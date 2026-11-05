# Xác minh ba mốc vận hành — 05/11/2026

## Backup/restore

PASS: backup PostgreSQL 17 thực tế bằng pg_dump custom, SHA-256 và manifest; nguồn ổn định trong khoảng sao lưu. Restore vào container PostgreSQL riêng không nối mạng; dấu vân tay cả 8 bảng khớp manifest. Dữ liệu: 3 repository, 2 commit, 6 task Notion, 2 migration; dữ liệu private nằm trong thư mục backups đã loại khỏi Git và source ZIP.

PASS: kiểm thử dữ liệu giả chứng minh checksum sai bị từ chối; không được restore vào DB active/tên không hợp lệ; có thể restore sang DB mới; tên đã tồn tại bị từ chối; dữ liệu nguồn giữ nguyên. CI đã bổ sung cùng bài kiểm tra này.

## HTTPS

PASS tại localhost: chạy bộ Compose production với DB restore riêng, PostgreSQL/API không publish cổng; Caddy chuyển HTTP sang HTTPS, chứng chỉ/hostname được xác minh bằng CA test. Health UP, shell/asset dashboard 200, CSP, API 401 khi thiếu key và request ID khớp. Overview đọc được 3 repository/6 task từ dữ liệu restore. Project test được dọn riêng sau kiểm tra.

Chưa triển khai Internet: người dùng chưa có server và tên miền. Chưa kiểm chứng DNS, ACME công khai, lịch backup trên server hoặc lưu backup ngoài máy. Có cấu hình/hướng dẫn để thực hiện khi đã có hạ tầng.

## GitHub/release

Source và lịch sử remote đã được kiểm tra. Kết quả CI/release và lượt sync thật qua HTTPS local sẽ được ghi vào báo cáo bàn giao sau khi hoàn tất publish. Không coi workflow đã viết là một lượt CI đã chạy thành công.
