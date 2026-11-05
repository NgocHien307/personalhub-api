# Notion: phục hồi lịch sử sync bị gián đoạn

Recovery chạy khi ứng dụng khởi tạo và trước mỗi lượt sync Notion mới. Không cần biến môi trường hoặc migration mới. Khi `NOTION_ENABLED=false`, recovery không truy cập database hay gọi Notion. Khi integration được bật, recovery vẫn chạy ở startup dù scheduler đang tắt.

## Hành vi

Ứng dụng thử lấy advisory transaction lock của data source đang cấu hình. Nếu khóa đang do worker khác giữ, recovery bỏ qua ngay. Khi lấy được khóa, các run RUNNING còn lại của source đó được chuyển thành FAILED, đặt finishedAt và thông báo:

```text
Notion sync interrupted; trigger sync again
```

Các run SUCCESS/FAILED/CONFLICT, run thuộc source khác, bảng GitHub, tasks và checkpoint giữ nguyên. Thời gian finishedAt là thời điểm phát hiện/ghi nhận sự gián đoạn, không phải thời điểm chính xác tiến trình đã dừng. Recovery lặp lại không sửa kết quả đã kết thúc.

Recovery chỉ cập nhật lịch sử, không tự chạy lại run cũ hoặc gọi Notion. Lượt manual/scheduler tiếp theo có ID mới, tiếp tục dùng checkpoint-overlap hiện có. Nếu startup bỏ qua do khóa đang bận, lượt sync sau sẽ thử recovery lại khi lấy được khóa.

## Giữ đúng run đang hoạt động

Một run mới chỉ được ghi RUNNING sau khi worker lấy được khóa. Trong lúc giữ khóa, worker dùng transaction riêng để commit recovery và bản ghi RUNNING. Nhờ vậy lịch sử vẫn hiện RUNNING trong khi đang gọi upstream, còn recovery từ một tiến trình khác sẽ thấy khóa bận và không sửa run đó.

Tasks, checkpoint và SUCCESS vẫn commit cùng một transaction chính. Nếu tải/lưu dữ liệu thất bại, transaction chính rollback; recovery đã commit không bị rollback theo. Trạng thái FAILED được ghi riêng. Trường hợp không lấy được khóa ghi CONFLICT riêng và trả HTTP 409 như trước.

Khóa PostgreSQL tự giải phóng khi transaction kết thúc hoặc kết nối bị đóng. Transaction `REQUIRES_NEW` dùng kết nối riêng trong khi transaction ngoài tiếp tục giữ khóa. Xem [PostgreSQL advisory locks](https://www.postgresql.org/docs/17/explicit-locking.html#ADVISORY-LOCKS) và [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html#tx-propagation-requires-new).

Cần pool ít nhất **2 kết nối** cho một worker Notion; cấu hình mặc định Hikari 10 kết nối hiện có đáp ứng điều này. Nếu tùy chỉnh pool, giữ đủ kết nối cho request và transaction lịch sử. Toàn bộ PersonalHub vẫn chạy một API instance/database vì GitHub dùng guard trong bộ nhớ; thay đổi này không bổ sung khả năng chạy nhiều replica cho toàn ứng dụng.

Nếu database vẫn không hoạt động khi ghi thất bại, run có thể còn RUNNING. Nó được xử lý khi startup hoặc lượt sync tiếp theo lấy được khóa và ghi database thành công. Không có vòng quét riêng định kỳ. Khi triển khai từ bản cũ, dừng instance cũ trước khi khởi động bản mới để mọi worker dùng cùng quy tắc ghi RUNNING sau khi lấy khóa.

## Xác minh

Automated tests kiểm tra integration tắt không truy cập DB, thứ tự lấy khóa trước khi ghi lịch sử, startup chỉ sửa đúng source, bảo vệ worker đang giữ khóa, recovery không ghi đè lịch sử kết thúc, dữ liệu/checkpoint không đổi và recovery vẫn tồn tại khi sync tiếp theo thất bại.

Recovery được kiểm tra trên PostgreSQL riêng với các run bị gián đoạn đã seed và Spring context mới; không cố dừng tiến trình đang sync dữ liệu của người dùng. Bài thử HTTP/rate limit hiện có tiếp tục dùng upstream giả lập. Kết quả thực tế ở [báo cáo recovery](NOTION_RECOVERY_VERIFICATION.md).

## Task list cho người dùng

[Checklist Markdown để dán vào Notion/GitHub Issue](checklists/PERSONALHUB_LIVE_CHECKLIST_VI.md) · [CSV đầu việc](checklists/PersonalHub.tasks.notion.csv) · [Nội dung file commit thử](checklists/github-live-test.sample.md)

Checklist dành cho một lượt kiểm tra dữ liệu thật mới của bạn. Chỉ đánh dấu sau khi thấy đủ kết quả; các bước người dùng chưa thực hiện không được coi là PASS nhờ automated tests.
