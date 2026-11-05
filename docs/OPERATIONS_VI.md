# Backup, phục hồi và triển khai HTTPS

Yêu cầu: Docker với Compose >= 2.24.4, Python >= 3.11. Chỉ chạy một API instance trên mỗi database. Các lệnh dưới đây chạy từ thư mục source; Windows dùng `python`, Linux thường dùng `python3`. Không đưa `.env` hoặc backup lên GitHub.

## 1. Sao lưu tại máy

Khi `db` đang chạy:

```powershell
python scripts/ops.py backup
```

Kết quả là ba file trong `backups/`: `.dump`, `.dump.sha256` và `.dump.manifest.json`. Giữ cả ba. File dump dùng định dạng custom của PostgreSQL, không xuất binary qua chuyển hướng PowerShell. Manifest lưu checksum và dấu vân tay từng bảng, không lưu token. Nếu `sourceStableDuringBackup=false`, có ghi dữ liệu trong khoảng đo: dump vẫn nhất quán theo snapshot của pg_dump, nhưng không thể khẳng định dấu vân tay khớp nguồn; thử lại lúc không sync để có bằng chứng đối chiếu đầy đủ.

Thay đường dẫn sau bằng file vừa tạo:

```powershell
python scripts/ops.py restore-check backups/personalhub-YYYYMMDDTHHMMSSZ-xxxxxxxx.dump
```

Lệnh kiểm checksum rồi restore vào container PostgreSQL 17 riêng, không nối mạng, không mở cổng và không gắn volume ứng dụng. Kết thúc sẽ xóa riêng container/volume test. `sourceFingerprintsMatch=true` chứng minh dữ liệu tất cả bảng khớp manifest khi nguồn ổn định. Công cụ không tự xóa backup cũ.

Dump chứa dữ liệu cá nhân. Thư mục được tạo với quyền 0700, file dump/manifest 0600 trên Linux; Windows dùng ACL của thư mục tài khoản, cần giới hạn người được đọc nếu máy dùng chung. Sao chép cả bộ sang nơi lưu trữ riêng có mã hóa. Mỗi ngày chạy backup bằng Task Scheduler/cron của máy hoặc server; kiểm restore định kỳ. Lịch và bản sao ngoài máy chưa được tự cài đặt.

## 2. Phục hồi dữ liệu mà không ghi đè DB hiện tại

Đầu tiên chạy `restore-check`. Sau đó tạo một database mới trong dịch vụ `db` đang chạy:

```powershell
python scripts/ops.py restore-new backups/personalhub-YYYYMMDDTHHMMSSZ-xxxxxxxx.dump --database personalhub_restore_20261105
```

Tên bắt buộc có tiền tố `personalhub_restore_`; nếu tên đã tồn tại thì từ chối. Lệnh không đổi database mà API sử dụng. Khi restore lỗi, giữ database mới để kiểm tra; không tự xóa dữ liệu.

Để chuyển API sang DB vừa phục hồi, dừng API trước, đặt `POSTGRES_DB=personalhub_restore_20261105` trong `.env`, rồi tạo lại `db`/`api` bằng Compose. Volume giữ nguyên. PostgreSQL không đổi mật khẩu role chỉ vì sửa `POSTGRES_PASSWORD` trong `.env`; mật khẩu phải khớp role đã tồn tại. Không dùng `docker compose down -v` trên hệ thống đang sử dụng.

Với môi trường local:

```powershell
docker compose stop api
# Sửa POSTGRES_DB trong .env thành tên DB đã restore.
docker compose up -d db api
```

Kiểm health, đăng nhập dashboard, so sánh repository/task/lịch sử sync trước khi bật scheduler. Có thể quay lại DB cũ bằng cách dừng API, khôi phục giá trị POSTGRES_DB trước đó, rồi chạy lại Compose. Bản dump chỉ chứa một database, không sao lưu role toàn cluster; Compose tạo role `personalhub` trên server mới và pg_restore bỏ owner/ACL để phục hồi bằng role đó.

## 3. Server HTTPS

Chuẩn bị một server Linux có Docker/Compose, Git, Python 3.11+, đủ chỗ cho Docker build và backup. Trỏ DNS A của tên miền tới server; chỉ đặt AAAA nếu IPv6 thực sự truy cập được. Mở TCP 80/443 và cổng SSH cần dùng. Chưa có server/tên miền thì chỉ thực hiện kiểm thử local ở mục 4.

Trên server, lấy source của release đã kiểm thử:

```bash
git clone https://github.com/NgocHien307/personalhub-api.git
cd personalhub-api
git checkout v0.1.0
umask 077
cp .env.example .env
chmod 600 .env
```

Điền `.env` trực tiếp trên server: mật khẩu database mới, API key riêng, GitHub username/token, Notion token/source và mapping thuộc tính. Với bảng đang dùng: `NOTION_TITLE_PROPERTY=Log title`, `NOTION_STATUS_PROPERTY=Status`, `NOTION_DUE_PROPERTY=Date`. Đặt `SITE_ADDRESS=hub.ten-mien-cua-ban.vn` bằng **tên miền thật**, không thêm path hoặc port. Lần đầu giữ `SYNC_ENABLED=false` và `NOTION_SYNC_ENABLED=false` để nghiệm thu thủ công.

```bash
docker compose -f compose.yaml -f compose.production.yaml config --quiet
docker compose -f compose.yaml -f compose.production.yaml up -d --build
```

Luôn dùng **cả hai** file, theo đúng thứ tự. Production bỏ port publish của DB và API; Caddy là điểm vào 80/443, tự cấp/gia hạn chứng chỉ và chuyển HTTP sang HTTPS khi DNS/cổng đúng. Volume `caddy_data` giữ tài liệu cấp chứng chỉ; không xóa khi cập nhật. Không bật Caddy access log để tránh ghi header riêng; log ứng dụng vẫn có request ID. Log Docker giới hạn 3 file × 10 MB mỗi service.

Mở `https://TEN-MIEN/actuator/health` và `https://TEN-MIEN/dashboard`. API key chỉ nhập vào dashboard của bạn. Không đăng `.env` lên chat hoặc repository. Nếu chuyển dữ liệu từ máy này: sao chép riêng ba file backup qua SSH, chạy `python3 scripts/ops.py --production restore-check DUONG_DAN_DUMP`, rồi `--production restore-new ... --database personalhub_restore_...` trước khi chuyển POSTGRES_DB. Không đóng gói dump vào release.

Chỉ một lần đồng bộ đủ để kiểm chứng cấu hình ban đầu: trigger GitHub, đợi SUCCESS, đối chiếu SHA commit; trigger Notion full sync, đối chiếu title/Status/Date. Sync lại phải giữ số bản ghi khi nguồn không thay đổi. Sau đó bật scheduler bạn muốn và chạy lại lệnh Compose `up -d`. Checklist chi tiết ở `docs/checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md`.

## 4. Kiểm thử bộ production qua HTTPS tại máy

```powershell
python scripts/verify_deployment.py backups/personalhub-YYYYMMDDTHHMMSSZ-xxxxxxxx.dump
```

Script dựng project Docker riêng, restore backup và dùng Caddy với hostname `localhost`, port động chỉ bind 127.0.0.1. Client tin đúng CA test qua file tạm và vẫn xác minh chứng chỉ/hostname; không cài CA vào Windows. Xác minh redirect, TLS, health, dashboard, API key và request ID, rồi dọn project test. Dữ liệu DB đang dùng không bị chuyển đổi. Báo cáo an toàn ở `target/deployment-verification.json`.

Thêm `--sync` để **chủ động gọi GitHub/Notion thật** và ghi kết quả vào DB test. `--expected-commit SHA` xác minh commit cụ thể đã nhập. Script không tạo/sửa task Notion hoặc commit GitHub. Đây là kiểm thử HTTPS local, không phải bằng chứng DNS hoặc chứng chỉ công khai.

## 5. Cập nhật, rollback và tra lỗi

Trước cập nhật: backup và kiểm restore; lưu tag đang chạy. Dừng API để tránh sync giữa chừng, checkout tag mới, rồi chạy lại Compose production `up -d --build`. Khi có migration mới, kiểm tính tương thích dữ liệu trước khi quay về tag cũ. Để rollback release này: checkout tag trước và rebuild; nếu schema không tương thích thì restore backup sang DB mới rồi chuyển cấu hình như mục 2.

Tra lỗi:

```bash
docker compose -f compose.yaml -f compose.production.yaml ps
docker compose -f compose.yaml -f compose.production.yaml logs --tail 100 caddy
docker compose -f compose.yaml -f compose.production.yaml logs --tail 100 api
python3 scripts/ops.py --production backup
```

Không chia sẻ raw log khi chưa che thông tin riêng. TLS lỗi: kiểm DNS/80/443, thời gian server, volume Caddy. 401: kiểm API key, không đổi mật khẩu PostgreSQL để sửa lỗi API. 502 của Notion: lấy requestId/syncRunId, xem lịch sử run và kiểm token/quyền kết nối source.

Tham khảo chính thức: [pg_dump PostgreSQL 17](https://www.postgresql.org/docs/17/app-pgdump.html), [pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html), [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https), [Docker Compose production](https://docs.docker.com/compose/how-tos/production/).
