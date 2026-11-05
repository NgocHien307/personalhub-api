# GitHub MVP verification — 4/4 hoàn thành

Kiểm tra ngày **03/11/2026**, khoảng **10:35–10:37 (UTC+7)**, với GitHub thật của owner `NgocHien307`. Chạy executable JAR hiện tại qua HTTP, dùng PostgreSQL test riêng; không mock GitHub hoặc persistence.

## Kết quả

| Bài kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Trigger sync thành công và chuyển SUCCESS | PASS | POST `/api/v1/sync/github` trả 202; quan sát đủ `QUEUED → RUNNING → SUCCESS`; trạng thái SUCCESS có trong PostgreSQL |
| Repository và commit thực được lưu đúng | PASS | 3 repositories, 1 commit ban đầu; so khớp metadata repo, repository ID/SHA, message, author login và committer timestamp với GitHub REST API |
| Sync hai lần không tăng commit khi nguồn không đổi | PASS | Lần 1 và 2: **1 → 1**; SHA set trên GitHub và PostgreSQL giữ nguyên. Sau bài 4, sync thêm tải lại **1 commit đã có**, tổng vẫn **2 → 2** |
| Tạo commit mới, sync lại và thấy cập nhật | PASS | Tạo 1 commit rỗng trên `main` của `NgocHien307/personalhub-api`; sync tiếp ghi nhận **1 → 2**, SHA mới tồn tại đúng 1 lần trong PostgreSQL và xuất hiện ở endpoint commits |

Repositories đã đối chiếu:

- [NgocHien307/personalhub-api](https://github.com/NgocHien307/personalhub-api)
- [NgocHien307/Dodge-Rift-Playable](https://github.com/NgocHien307/Dodge-Rift-Playable)
- [NgocHien307/boilerplate-npm](https://github.com/NgocHien307/boilerplate-npm)

Phạm vi sync: tối đa 3 repo public thuộc owner, nhánh mặc định, 30 ngày gần nhất cho lần đầu. Hai repo cũ không có commit trong cửa sổ này; việc lưu metadata repo vẫn được xác nhận. Các lần sau dùng checkpoint và overlap mặc định 60 phút.

## Commit kiểm tra trên GitHub

- SHA: [`7f9722e37985a629171d361869c907947b087966`](https://github.com/NgocHien307/personalhub-api/commit/7f9722e37985a629171d361869c907947b087966)
- Message: `test: verify PersonalHub GitHub sync`
- Nhánh: `main`; thời điểm commit: **10:35:11 UTC+7, 03/11/2026**.
- Parent: `4b139cb26be0aa1dd9a81a75258142f9798e301b`.
- Commit rỗng: dùng cùng tree `a4917bb630ec151664a68f3f3083234d69ea9d98` với parent. Cập nhật ref bằng fast-forward, `force=false`.

Danh sách commit public ban đầu chưa phản ánh SHA vừa tạo dù ref đã cập nhật. Đọc lại sau đó đã thấy SHA; tiếp tục kiểm thử trên cùng database và cùng commit. Chỉ tạo **một** commit kiểm tra, không tạo thêm khi chạy tiếp.

## Các lượt sync đã xác minh

| Run ID | Kết quả | Repos xử lý | Commit tải về | Commit trong DB sau sync |
| --- | --- | --- | --- | --- |
| `9c030c99-da72-44a5-8e50-245859cd4b11` | SUCCESS | 3 | 1 | 1 |
| `e09a42fd-2c82-480e-a3d0-c07a6bfce9b1` | SUCCESS | 3 | 0 | 1 |
| `ff7e4792-6a93-4769-92ac-2c055c6a7300` | SUCCESS | 3 | 1 | 2 |
| `3aa04ef2-bbae-438a-b5e9-fee7de8dcd0e` | SUCCESS | 3 | 1 đã lưu | 2 |

Lượt 2 không tải commit cũ vì đã nằm ngoài cửa sổ overlap. Lượt 4 xác minh thêm nhánh upsert bằng payload thực đã tồn tại: tải lại commit mới của bài 4, không tăng số bản ghi và không đổi SHA set.

Bằng chứng có cấu trúc, gồm trạng thái từng lượt, counts, SHA và checksum JAR: [github-live-evidence.json](github-live-evidence.json).

## Môi trường và giới hạn

- Spring Boot 4.1.2, JAR biên dịch Java target 21; runtime JDK 23.0.2, PostgreSQL 18.3 cài sẵn. Chưa xác minh Docker Java 21/PostgreSQL 17.
- Ứng dụng test chỉ nghe `127.0.0.1:18081`, database riêng `127.0.0.1:55448`. Cả ứng dụng và database test đã dừng sau kiểm tra; không dùng database ứng dụng hiện có.
- Ứng dụng đọc GitHub public API thật, không dùng token; thao tác tạo commit dùng phiên GitHub CLI hiện có của owner. Không lưu API key/token trong báo cáo, source hoặc gói ZIP.
- Không thay đổi production code trong đợt kiểm tra này. Kết quả áp dụng cho phạm vi 3 repo và cửa sổ thời gian trên; chưa kiểm tra private repositories, toàn bộ lịch sử, hoặc tải lớn/rate limit.
