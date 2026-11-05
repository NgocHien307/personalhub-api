# Xác minh ba mốc vận hành — 05/11/2026

## Backup/restore

PASS: backup PostgreSQL 17 thực tế bằng pg_dump custom, SHA-256 và manifest; nguồn ổn định trong khoảng sao lưu. Restore vào container PostgreSQL riêng không nối mạng; dấu vân tay cả 8 bảng khớp manifest. Dữ liệu: 3 repository, 2 commit, 6 task Notion, 2 migration; dữ liệu private nằm trong thư mục backups đã loại khỏi Git và source ZIP.

PASS: kiểm thử dữ liệu giả chứng minh checksum sai bị từ chối; không được restore vào DB active/tên không hợp lệ; có thể restore sang DB mới; tên đã tồn tại bị từ chối; dữ liệu nguồn giữ nguyên. CI đã bổ sung cùng bài kiểm tra này.

## HTTPS

PASS tại localhost: chạy bộ Compose production với DB restore riêng, PostgreSQL/API không publish cổng; Caddy chuyển HTTP sang HTTPS, chứng chỉ/hostname được xác minh bằng CA test. Health UP, shell/asset dashboard 200, CSP, API 401 khi thiếu key và request ID khớp. Overview đọc được 3 repository/6 task từ dữ liệu restore. Project test được dọn riêng sau kiểm tra.

Chưa triển khai Internet: người dùng chưa có server và tên miền. Chưa kiểm chứng DNS, ACME công khai, lịch backup trên server hoặc lưu backup ngoài máy. Có cấu hình/hướng dẫn để thực hiện khi đã có hạ tầng.

## Nghiệm thu upstream thật qua HTTPS local

PASS: GitHub sync hai lần đều SUCCESS, xử lý 3 repository; số commit sau lượt hai không tăng. Commit công bố source `dcc24841e08721600d830f89f9814b3fc8a7fdad` đã được nhập vào PostgreSQL test. Notion full sync hai lần SUCCESS, mỗi lượt 6 task; số task giữ nguyên, có dữ liệu Date. Dashboard overview qua HTTPS trả 3 repository/6 task. Các POST chỉ cập nhật database test; không tạo/sửa task Notion hoặc commit GitHub trong bài nghiệm thu này.

## GitHub/release

Source đã đưa vào main qua [PR #1](https://github.com/NgocHien307/personalhub-api/pull/1), giữ lịch sử ban đầu. [CI trên main dcc2484](https://github.com/NgocHien307/personalhub-api/actions/runs/41968644240) PASS cả 4 job: unit-tests, integration-tests, dashboard-tests và operations-tests. Các lượt CI dùng Testcontainers thật; 95 unit + 22 integration tests Java và 11 client tests PASS. GitGuardian trên PR cũng PASS; package scan local không tìm thấy giá trị secrets hiện có trong `.env` hoặc credential-pattern.

Phiên bản bàn giao 0.1.0; [trang release v0.1.0](https://github.com/NgocHien307/personalhub-api/releases/tag/v0.1.0) là nơi kiểm tra trạng thái công bố và tải source ZIP/SHA-256. Release chỉ được tạo sau CI của thay đổi cuối cùng. Xem bằng chứng máy đọc được trong [operations-evidence.json](operations-evidence.json).
