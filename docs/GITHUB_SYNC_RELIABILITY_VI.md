# GitHub sync: retry và khởi động lại

Mốc này bổ sung khả năng chịu lỗi cho GitHub trên kiến trúc hiện có. Không đổi endpoint, response thành công, migration V1/V2 hay POM. Không cần thêm biến môi trường. Chỉ chạy **một API instance trên mỗi database**; cơ chế chống sync đồng thời và khôi phục khi startup chưa hỗ trợ nhiều replica.

## Retry từng trang dữ liệu

- Mỗi request GET repository/commit có tối đa **3 lần gọi**, gồm lần đầu và 2 lần retry. Chỉ gọi lại trang đang lỗi, không thêm lại các trang trước vào kết quả.
- Lỗi HTTP 5xx và lỗi kết nối/timeout dùng khoảng chờ 1 rồi 2 giây. Lỗi JSON không hợp lệ không được retry.
- HTTP 429 được coi là rate limit. HTTP 403 chỉ được retry khi có `Retry-After` hoặc `X-RateLimit-Remaining: 0`; 403 không có dấu hiệu này kết thúc ngay, tránh gọi lại lỗi quyền truy cập.
- Tôn trọng `Retry-After` dạng số giây hoặc HTTP date. Khi quota còn 0 và có `X-RateLimit-Reset`, chờ đến sau thời điểm reset; nếu có cả hai hướng dẫn thì dùng thời gian muộn hơn.
- Rate limit không có hướng dẫn thời gian: chờ ít nhất 60 giây. Nếu vẫn bị giới hạn, backoff tiếp theo vượt trần 60 giây nên kết thúc run.
- **Mỗi lần chờ tối đa 60 giây.** Nếu GitHub yêu cầu chờ lâu hơn, run FAILED ngay để thử ở lượt sync mới; không rút ngắn khoảng chờ của GitHub. Header thời gian không hợp lệ cũng làm run FAILED.
- HTTP 401/404/422 và 403 thông thường không retry. HTTP 409 ở endpoint commits vẫn được xử lý như repository rỗng theo hành vi MVP hiện tại.
- Khi thread bị ngắt trong lúc chờ, dừng retry và giữ cờ interrupt.

Quy tắc rate limit dựa trên [hướng dẫn chính thức của GitHub](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api#handle-rate-limit-errors-appropriately). Trần 3 lần gọi và 60 giây là lựa chọn của PersonalHub để giới hạn thời gian giữ worker; không phải giới hạn do GitHub đặt.

## Run bị gián đoạn

Khi coordinator được khởi tạo, trước khi nhận request hoặc tick scheduler, một transaction cập nhật các GitHub run `QUEUED`/`RUNNING` còn trong database thành `FAILED`, đặt `finishedAt` và thông báo:

```text
GitHub sync interrupted by application restart; trigger sync again
```

Run SUCCESS/FAILED có sẵn giữ nguyên trạng thái, số lượng và thời gian hoàn tất. Khởi động lại nhiều lần không ghi đè kết quả recovery trước đó. Không tự chạy lại job cũ; bạn có thể gửi **Trigger GitHub sync** để tạo run ID mới. `SYNC_ENABLED=false` tiếp tục chỉ tắt scheduler, không chặn sync thủ công.

Dữ liệu và checkpoint từng repository vẫn được ghi trong cùng một transaction. Repository đã hoàn tất trước lỗi giữ nguyên dữ liệu; transaction đang dở bị rollback. Lượt sync tiếp theo dùng checkpoint và overlap hiện có, còn khóa `(repository_id, sha)` chống trùng commit.

Nếu executor từ chối nhận job sau khi tạo bản ghi QUEUED, coordinator chuyển bản ghi thành FAILED và mở lại quyền trigger. Nếu database lỗi ngay lúc ghi trạng thái thất bại, run có thể còn kẹt cho đến lần khởi động lại có database hoạt động. Không có cơ chế tự động cứu run ngay trong cùng tiến trình.

Số lượng trên run bị crash có thể chưa phản ánh các repository đã commit trước đó vì bộ đếm chỉ được lưu khi kết thúc run. Dữ liệu thực và checkpoint của repository vẫn là căn cứ cho lượt sync sau.

## Lịch sử lỗi và phạm vi

REST client chuyển lỗi GitHub thành thông báo do ứng dụng tạo, ví dụ `GitHub HTTP 401`. Worker không lưu hoặc log nguyên exception message, response body, URL hay authorization headers. Lỗi không thuộc REST client dùng thông báo chung để tránh lộ nội dung dữ liệu hoặc cấu hình.

Recovery GitHub này chỉ cập nhật bảng `sync_runs`. Mốc sau đã bổ sung [recovery Notion dưới khóa của source](NOTION_RECOVERY_VI.md); retry GitHub và REST contract giữ nguyên. Cần distributed lease/locking cho GitHub trước khi chạy nhiều replica API cùng database.

## Kiểm tra lại

Với JDK 21 và Docker:

```text
mvn -B test
mvn -B verify
```

`GitHubRetryTest` dùng HTTP giả lập và đồng hồ/sleep giả để kiểm tra thời gian chờ mà không gửi hàng loạt request gây rate limit cho GitHub thật. Các test worker kiểm tra checkpoint/partial failure và log an toàn. Integration tests dùng PostgreSQL riêng để xác minh rollback và callback khởi tạo coordinator với run QUEUED/RUNNING đã có sẵn; kiểm tra run kết thúc và bảng Notion không bị sửa.

Các kết quả chạy thực tế được ghi trong [báo cáo xác minh](GITHUB_SYNC_RELIABILITY_VERIFICATION.md).
