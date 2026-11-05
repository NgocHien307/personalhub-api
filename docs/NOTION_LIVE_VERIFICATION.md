# Kiểm tra Notion thật — 6/6 PASS — 03/11/2026

Đã kiểm tra ứng dụng Docker đang chạy trên Java 21, Spring Boot 4.1.2 và PostgreSQL 17.11, với Notion API thật. Báo cáo này bổ sung cho 38 unit tests và 7 integration tests trước đó; không thay thế kết quả CI trên Java 21/Testcontainers.

## Kết quả đã xác minh

| Bài kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Health ứng dụng | PASS | `/actuator/health` trả UP |
| Full sync thủ công | PASS | Hai lượt liên tiếp trả HTTP 200 và SUCCESS |
| Lưu dữ liệu đúng | PASS | Ban đầu 5, sau thao tác người dùng 6 bản ghi PostgreSQL khớp với Notion: ID, title, status, start/end/time zone, last edited time và archived |
| Sync lặp khi nguồn không đổi | PASS | Ban đầu 5 → 5; sau tạo mới 6 → 6; nội dung và tập ID không đổi khi full sync đọc lại |
| Checkpoint | PASS | Tiến lên sau lượt sync thành công |
| Scheduler | PASS | Log NotionSyncScheduler và run lưu trong DB trùng ID, trạng thái SUCCESS; kiểm tra chu kỳ 60 giây |
| Sửa bản ghi trong Notion | PASS | 1 bản ghi cũ đổi Status và Date, giữ nguyên ID; các trường trong PostgreSQL và REST khớp nguồn |
| Tạo bản ghi mới trong Notion | PASS | 1 bản ghi mới có Status/Date, tổng 5 → 6; có đúng một bản ghi theo ID trong PostgreSQL và REST |

Lượt scheduler đầu xử lý 0 bản ghi vì không có thay đổi trong cửa sổ incremental. Bài scheduler xác minh tự khởi tạo job và lưu SUCCESS; hai bài sửa/tạo bản ghi được xác minh bằng sync thủ công, không phải bằng việc chờ scheduler tải một thay đổi mới.

Sau bài kiểm tra, đã giữ `NOTION_SYNC_ENABLED=true`, đặt `NOTION_SYNC_FIXED_DELAY_MS=1800000` (30 phút), tạo lại container và xác nhận cấu hình trong container đang chạy. Health cuối cùng là UP. Chu kỳ 60 giây chỉ dùng trong lượt xác minh tự chạy.

## Cấu hình đã kiểm tra

Nguồn đang dùng có cột title `Log title`, trạng thái `Status`, ngày `Date`. Cấu hình ánh xạ tương ứng nằm trong `.env` cục bộ:

```dotenv
NOTION_TITLE_PROPERTY='Log title'
NOTION_STATUS_PROPERTY=Status
NOTION_DUE_PROPERTY='Date'
```

`.env.example` tiếp tục dùng các tên mặc định để người triển khai tự chọn. Không đưa `.env` thật, token, API key hoặc nội dung task vào gói source.

Trong quá trình tiếp tục kiểm tra, Docker engine ban đầu không hoạt động. Đã bật Docker và khởi động các container hiện có; giữ nguyên volume dữ liệu. Kết quả PASS chỉ được ghi nhận sau khi ứng dụng trở lại UP và các phép đối chiếu thực sự hoàn tất.

## Xác minh sửa và tạo bản ghi

Người dùng đã sửa và tạo bản ghi thử trong Notion, sau đó báo hoàn tất. So sánh với mốc 5 bản ghi đã lưu phát hiện đúng 1 ID cũ có thay đổi ở các trường ánh xạ và 1 ID mới. Từ dữ liệu DB trước sync, các trường đổi quan sát được là `status` và `dueStart`; không ghi nhận thay đổi title ở bản ghi cũ.

Incremental sync trả HTTP 200/SUCCESS và lưu đủ 6 bản ghi. Từng ID sửa/tạo có đúng một bản ghi trong PostgreSQL; endpoint `/api/v1/notion/tasks/{id}` trả các giá trị tương ứng với nguồn. Full sync tiếp theo đọc lại cả 6 bản ghi và giữ nguyên nội dung, số lượng, tập ID. Notion không thay đổi trong khoảng đối chiếu. Trước incremental sync, DB chưa chứa đầy đủ thay đổi này, nên đây là kiểm tra cập nhật thực sự.

Không tự sửa hoặc tạo nội dung Notion bằng token của người dùng. Báo cáo gồm 6 nhóm kiểm tra PASS: manual sync, đối chiếu dữ liệu, chống trùng, scheduler, sửa bản ghi và tạo mới.

Bằng chứng an toàn để chia sẻ: [notion-live-evidence.json](notion-live-evidence.json). Mốc đối chiếu chi tiết chỉ lưu ID và fingerprint trong `target/notion-live-baseline.json` trên máy, không nằm trong ZIP.
