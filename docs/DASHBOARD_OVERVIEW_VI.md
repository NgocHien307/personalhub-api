# API tổng quan và mã theo dõi request

## Đọc dữ liệu tổng quan

**GET `/api/v1/dashboard/overview?days=30`**, yêu cầu `X-API-Key`. `days` từ 1 đến 365, mặc định 30. Tham số sai trả 400 theo Problem Details hiện có.

Endpoint đọc PostgreSQL, không gọi GitHub/Notion hoặc tự trigger sync. Các query chạy trong transaction read-only, REPEATABLE READ để đọc cùng snapshot database. Xem [PostgreSQL transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-REPEATABLE-READ).

| Trường | Ý nghĩa |
| --- | --- |
| generatedAt | Thời điểm tạo response, không phải thời điểm đồng bộ nguồn |
| days | Cửa sổ GitHub được yêu cầu |
| github | Cùng thống kê với GET github/stats: username, since, trackedRepositories, recordedCommits, authoredCommits, activeDaysUtc |
| notion.enabled | Integration hiện có được bật không |
| notion.activeTasks | Tổng task chưa archived của source đang cấu hình; không bị giới hạn bởi pagination |
| notion.archivedTasks | Tổng task archived của source đang cấu hình |
| notion.tasksByStatus | Nhóm task chưa archived theo đúng tên trạng thái; null là chưa có trạng thái |
| notion.lastCheckpointAt | Watermark của lần sync Notion thành công gần nhất; null nếu chưa có |
| latestSyncs.github | GitHub run mới nhất hoặc null, cùng định dạng endpoint lịch sử |
| latestSyncs.notion | Notion run mới nhất của source hiện tại hoặc null, cùng định dạng endpoint lịch sử |

`days` chỉ giới hạn commit GitHub trong một khoảng thời gian liên tục tính ngược từ lúc query; thống kê ngày hoạt động dùng UTC. Số repository là toàn bộ cache hiện tại. Thống kê Notion bao gồm toàn bộ cache của source, không lọc theo Date hoặc days. Trạng thái là tên gốc, không suy luận Done/Overdue từ nhãn tùy ý.

Khi Notion tắt, endpoint vẫn trả 200: enabled=false, các số đếm bằng 0, tasksByStatus rỗng, checkpoint và latestSyncs.notion null. Nguồn bật nhưng chưa sync có enabled=true với các số đếm 0. Run mới nhất có thể FAILED trong khi dữ liệu cũ và checkpoint thành công vẫn còn; frontend cần hiển thị trạng thái run và thời gian dữ liệu riêng biệt.

Dữ liệu GitHub giữ phạm vi MVP hiện có: cache các public repo đã theo dõi, commit theo default branch và cửa sổ sync. Đổi username không tự xóa cache cũ. [Giao diện dashboard](DASHBOARD_UI_VI.md) đã dùng endpoint này; lịch sử metrics/Prometheus vẫn là backlog.

## Dùng trong Postman hoặc PowerShell

Import lại collection cập nhật rồi gửi **Read API → Dashboard overview**. Request này chạy được khi `runSync=false`. Request **Request ID echo** minh họa cách gửi UUID chủ động; **Invalid overview window** minh họa lỗi 400.

Trong PowerShell mới, chạy từng dòng:

```powershell
$secureKey = Read-Host 'Nhập PERSONALHUB_API_KEY trên máy của bạn' -AsSecureString
$headers = @{ 'X-API-Key' = [System.Net.NetworkCredential]::new('', $secureKey).Password }
$response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/v1/dashboard/overview?days=30' -Headers $headers
$response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
$requestId = $response.Headers['X-Request-ID']
$requestId
docker compose logs --since 10m api | Select-String -SimpleMatch $requestId
```

Không gửi key vào chat/Issue. Key backend và token upstream tiếp tục chỉ được cấu hình riêng trên máy.

## Request ID và log

Mỗi request đi qua security filter nhận response header **X-Request-ID**. Header gửi vào có dạng UUID chuẩn 36 ký tự được giữ lại và chuẩn hóa chữ thường. Giá trị thiếu, sai hoặc quá dài được thay bằng UUID mới, không làm request thất bại chỉ vì ID không hợp lệ. ID không thay thế API key.

Các lỗi do Security/MVC xử lý có thêm trường `requestId`, khớp response header. Các trường lỗi cũ và response thành công giữ nguyên. `syncRunId` trên lỗi Notion 502 vẫn là ID của job; requestId là ID của HTTP request. Job GitHub async và scheduler tiếp tục dùng run ID trong log/lịch sử; mốc này chưa truyền trace qua các tiến trình hoặc tự gắn MDC sang worker async.

Log hoàn tất request dùng các trường requestId, method, route, status, durationMs. Route là pattern do MVC chọn, ví dụ `/api/v1/notion/tasks/{id}`, hoặc unmatched nếu request chưa đến controller. Không log raw URL/query, path ID thực, header auth hoặc request/response body. MDC được dọn/khôi phục sau request để không lẫn ID giữa các request trên cùng thread.

Ví dụ log với ID minh họa:

```text
HTTP request completed requestId=a0000000-0000-0000-0000-000000000001 method=GET route=/api/v1/dashboard/overview status=200 durationMs=12
```

Filter xử lý các request Servlet thông thường của ứng dụng; lỗi bị proxy, HTTP parser hoặc firewall chặn trước filter có thể không có header này. Không có thay đổi mở thêm endpoint Actuator.

## Xác minh và bước tiếp

Xem [báo cáo kiểm tra mốc này](DASHBOARD_OVERVIEW_VERIFICATION.md) và [checklist thực hành](checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md). OpenAPI mô tả 14 thao tác; Postman có 24 request, 3 trigger sync tiếp tục chỉ chạy khi runSync=true.

Các phần còn lại: metrics sync có lưu lịch sử, cấu hình triển khai HTTPS và backup/restore PostgreSQL. Giữ một API instance/database cho tới khi có distributed guard GitHub.
