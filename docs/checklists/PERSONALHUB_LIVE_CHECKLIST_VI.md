# PersonalHub — checklist kiểm tra Notion và GitHub

Lượt kiểm tra: **PH-LIVE-20261104**. Copy nội dung này vào một trang Notion hoặc phần mô tả GitHub Issue. File `PersonalHub.tasks.notion.csv` bên cạnh chứa các đầu việc để nhập thành bảng quản lý; Status chọn theo các trạng thái có sẵn trong workspace của bạn.

Module GitHub hiện đồng bộ **repository và commit**. GitHub Issue giúp theo dõi checklist; để kiểm tra dữ liệu GitHub, hãy tạo một commit trên repo public mà bạn sở hữu, ở default branch và trong phạm vi các repo đang được sync.

Module Notion đọc các thuộc tính của bảng/data source đang cấu hình: `Log title`, `Status`, `Date`. Tạo bản ghi mẫu trong đúng bảng đang kết nối. Nếu nhập CSV thành bảng mới để quản lý checklist, bảng mới đó chỉ được PersonalHub sync khi được cấp quyền và cấu hình đúng data source; không cần đổi cấu hình để thực hiện các bước dưới đây trên bảng hiện tại.

## Chuẩn bị

- [ ] **PH-P01 — Lưu số liệu ban đầu.** Health UP, ghi số task Notion, repository và commit hiện có. Lượt trước có 6 task, 3 repository, 2 commit; dùng số thực tế lúc bạn bắt đầu. Nếu import checklist vào bảng đang sync, đếm mốc sau khi import và sync xong.
- [ ] **PH-P02 — Chuẩn bị Postman.** Chọn environment local, `baseUrl=http://localhost:8080`, API key lưu riêng trên máy. Đặt `runSync=true` để gửi các request trong folder Sync (opt in).

## Notion

- [ ] **PH-N01 — Tạo mới.** Trong bảng đang kết nối, thêm `Log title=PH-LIVE-20261104 sample v1`, chọn một Status có sẵn, đặt Date `2026-11-04`. Chạy **Trigger Notion full sync**. Đạt khi HTTP 200, run SUCCESS, tìm thấy task có đúng title/status/dueStart và số bản ghi tăng 1 so với mốc đã ổn định. Ghi lại `id`.
- [ ] **PH-N02 — Cập nhật.** Sửa cùng dòng thành `PH-LIVE-20261104 sample v2`, đổi Status và Date sang `2026-11-05`. Chạy **Trigger Notion incremental sync**. Đạt khi dữ liệu thay đổi đúng, `id` giữ nguyên và tổng số task không tăng.
- [ ] **PH-N03 — Chống trùng.** Không sửa dữ liệu nguồn, sync full hai lần. Đạt khi cả hai run SUCCESS và tổng số task/ID giữ nguyên. `tasksProcessed` là số task đọc/xử lý trong lượt, không phải số task mới được thêm.
- [ ] **PH-N04 — Scheduler.** Sửa title thành `PH-LIVE-20261104 sample v3`; giữ nguyên dữ liệu sau đó và không bấm sync thủ công. Chờ tới tick tiếp theo theo cấu hình; local hiện dùng chu kỳ 30 phút. Đạt khi task tự cập nhật và có log scheduler cùng ID với một run SUCCESS. Nếu scheduler đang tắt thì ghi SKIPPED, không đánh PASS.

## GitHub

- [ ] **PH-G01 — Tạo commit thử.** Trên repo public bạn sở hữu, ví dụ `NgocHien307/personalhub-api` nếu repo này vẫn đang được theo dõi, thêm file `verification/personalhub-live-test.md` bằng nội dung trong `github-live-test.sample.md`. Commit vào default branch với message `test: verify PersonalHub live sync PH-LIVE-20261104`. Ghi SHA và link commit. Commit này phải nằm trong khoảng thời gian sync hiện tại.
- [ ] **PH-G02 — Đồng bộ commit.** Gửi **Trigger GitHub sync**. Đạt khi HTTP 202, run chuyển sang SUCCESS và query commits trả đúng SHA, repository, message, thời gian commit. `recordedCommits` tăng theo số commit mới thực tế; `authoredCommits` chỉ tăng nếu GitHub gắn author.login đúng tài khoản đang cấu hình.
- [ ] **PH-G03 — Chống trùng.** Không tạo thêm commit, chạy sync GitHub hai lần. Đạt khi cả hai SUCCESS, SHA thử chỉ xuất hiện một lần trong repository và tổng commit không tăng. Repository mới có thể cần nằm trong `SYNC_MAX_REPOSITORIES`; dữ liệu query có phân trang, phải tìm trong đủ các trang.

## API và dữ liệu sau restart

- [ ] **PH-A01 — Xác thực.** Health không cần key trả 200/UP. Một endpoint `/api/v1/**` thiếu/sai key trả 401 với code UNAUTHORIZED; key đúng đọc được dữ liệu. Không đưa key hoặc token vào Issue/Notion/report.
- [ ] **PH-R01 — Giữ dữ liệu.** Khi không có run đang chạy, dùng `docker compose restart api`, chờ health UP rồi đọc lại mẫu Notion và SHA GitHub. Đạt khi ID, dữ liệu và số lượng không thay đổi. Không xóa volume. Bài kiểm tra này chứng minh dữ liệu tồn tại qua restart; recovery run bị gián đoạn được kiểm tra riêng trong automated tests trên database test.
- [ ] **PH-V01 — Lưu bằng chứng.** Điền mẫu kết quả bên dưới, chỉ đánh dấu các bước đã quan sát đủ điều kiện. Đặt `runSync=false` sau khi kiểm tra để tránh vô tình gửi lại POST khi chạy collection.

## API tổng quan và tra lỗi

- [ ] **PH-O01 — Tổng quan.** Gửi **Dashboard overview** trong Postman. Đạt khi HTTP 200, số repository/commit khớp GitHub stats cùng days, activeTasks + archivedTasks khớp cache Notion của source và tổng nhóm trạng thái bằng activeTasks. GET này không trigger sync; generatedAt là lúc tạo response, không phải độ mới của nguồn.
- [ ] **PH-O02 — Mã request.** Gửi **Request ID echo**, rồi **Invalid overview window**. Đạt khi request đầu trả lại đúng X-Request-ID; lỗi 400 có requestId khớp header và tra được một log hoàn tất tương ứng. Chạy lại không có key phải trả 401 cùng cơ chế ID. Xem [cách tra log bằng PowerShell](../DASHBOARD_OVERVIEW_VI.md).

## Giao diện dashboard

- [ ] **PH-U01 — Kết nối.** Mở `/dashboard`, nhập PERSONALHUB_API_KEY trong .env. Đạt khi vào Tổng quan, số liệu khớp API và ô nhập khóa đã được xóa.
- [ ] **PH-U02 — Danh sách.** Chuyển GitHub/Notion, thử bộ lọc repository, trạng thái, archived và phân trang. Đạt khi nội dung đúng điều kiện và link mở đúng nguồn.
- [ ] **PH-U03 — Đồng bộ.** Trong Đồng bộ, trigger từng nguồn cần thử; quan sát trạng thái và chọn đúng Nguồn trong lịch sử. Đạt khi dữ liệu mới xuất hiện và số đã xử lý không bị hiểu nhầm là số thêm mới.
- [ ] **PH-U04 — Mobile và phiên.** Thu nhỏ cửa sổ/điện thoại, thử điều hướng bằng bàn phím. Ngắt kết nối rồi reload; đạt khi không tràn ngang, dữ liệu cũ biến mất và cần nhập khóa lại. Xem [hướng dẫn](../DASHBOARD_UI_VI.md).

## Request đối chiếu

Các request dưới đây dùng API key của environment Postman, ngoại trừ health:

| Thao tác | Request |
| --- | --- |
| Health | GET `/actuator/health` |
| Notion full | POST `/api/v1/notion/sync?full=true` |
| Notion incremental | POST `/api/v1/notion/sync` |
| Tasks | GET `/api/v1/notion/tasks?page=0&size=100` |
| Task mẫu | GET `/api/v1/notion/tasks/{id}` |
| Run Notion | GET `/api/v1/notion/sync-runs?limit=20` |
| Trigger GitHub | POST `/api/v1/sync/github` |
| Run GitHub | GET `/api/v1/sync/runs/{id}` |
| Repositories | GET `/api/v1/github/repositories?limit=100` |
| Commits | GET `/api/v1/github/commits?page=0&size=100&repoId={repositoryId}` |
| Statistics | GET `/api/v1/github/stats?days=30` |

Nếu task/commit vượt kích thước trang, đọc tiếp `page=1,2,...`. Tasks API trả mảng, không có totalElements; không dùng độ dài một trang làm tổng số dữ liệu khi còn trang tiếp theo.

## Mẫu kết quả để lưu vào Issue/Notion

```text
Lượt: PH-LIVE-20261104
Thời gian kiểm tra:
Health:
Notion: baseline tasks= ; sau tạo= ; sau sửa= ; sau replay=
Notion task ID:
Notion scheduler run ID / log đối chiếu:
GitHub repo:
Commit SHA / link:
GitHub: baseline commits= ; sau commit mới= ; sau replay=
GitHub sync run IDs:
Sau restart: PASS / FAIL
Các bước SKIPPED / lỗi còn lại:
```

## Công việc phát triển sau checklist

- [x] GitHub retry/backoff, phục hồi run khi startup, che nội dung lỗi — đã xác minh ở mốc trước.
- [x] Notion: phục hồi run bị gián đoạn mà không sửa run đang giữ khóa; tests và kiểm tra API mới đã PASS. Xem [báo cáo recovery](../NOTION_RECOVERY_VERIFICATION.md).
- [x] Theo dõi HTTP bằng request ID, log method/route/status/thời gian và lỗi có mã đối chiếu.
- [x] API tổng hợp GitHub + Notion cho dashboard; OpenAPI/Postman và tests đã cập nhật.
- [ ] Chỉ số sync có lưu lịch sử và giám sát vận hành.
- [x] Dashboard có Tổng quan/GitHub/Notion/Đồng bộ, responsive và cơ chế kết nối bằng key trong bộ nhớ.
- [ ] Triển khai HTTPS và backup/restore PostgreSQL.

Các đầu việc cuối là backlog, chưa được đánh dấu hoàn thành. Checklist dữ liệu thật là lượt kiểm tra của bạn; kết quả automated tests được báo riêng trong tài liệu xác minh.
