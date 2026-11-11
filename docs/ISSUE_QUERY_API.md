# Chi tiết, tìm kiếm và xóa issue

Bearer JWT bắt buộc. GET `/api/issues/{issueId}` trả IssueResponse có version, cần VIEW_PROJECT trên project sở hữu issue.

GET `/api/projects/{projectId}/issues/search` cần VIEW_PROJECT, hỗ trợ:

| Query | Ý nghĩa |
|---|---|
| keyword | Tìm key/summary/description không phân biệt hoa thường; `%`, `_`, `\` là ký tự thường |
| statusId, typeId | ID issue_statuses/issue_types |
| assigneeId, reporterId | ID users nhận việc/báo cáo |
| unassigned=true | Chưa giao ai; không dùng cùng assigneeId |
| sprintId | ID sprints, phải thuộc project trên URL |
| priority | Giá trị priority chính xác, không phân biệt hoa thường |
| page, size | 0-based, mặc định 0/20, size tối đa 100 |
| sort, direction | issueNumber/summary/priority/createdAt/updatedAt và ASC/DESC; mặc định createdAt DESC, thêm issueId để ổn định |

Ví dụ `/api/projects/10/issues/search?keyword=login&priority=HIGH&unassigned=true&page=0&size=20`. Mọi filter kết hợp AND với projectId; keyword OR trên ba trường. Trả `{items:[],page:0,size:20,totalItems:0,totalPages:0}`. ID filter không có dữ liệu trả trang rỗng; sprint không tồn tại 404, sprint khác project 400.

DELETE `/api/issues/{issueId}?expectedVersion=3` cần DELETE_ISSUE. Version khác bản ghi hiện tại trả 409 để tải lại. Còn issue con hoặc attachment trả 409: xử lý riêng trước, không tự xóa cây hoặc tệp từ xa. Thành công 204.

Xóa metadata của đúng issue (notification, comment, transition, rank board, link sprint, worklog và link version), rồi issue và audit cùng transaction. Không xóa project/user/status/định nghĩa version. Lỗi giữa bước rollback. Đây là xóa vật lý, có thể làm thay đổi thống kê dựa trên dữ liệu hiện tại; không phải lưu trữ lịch sử đầy đủ.

Migration triển khai: chạy `scripts/migrations/003_issue_sequence.sql` bằng `psql -v ON_ERROR_STOP=1` sau backup. Counter projects.issue_sequence giữ số đã cấp khi xóa issue, create cấp lớn hơn counter và MAX hiện tại. Migration chạy lặp không hạ counter; không thể suy ra số đã xóa trước khi có counter nếu dữ liệu không còn. Phiên này chỉ chạy trên DB test.

Kiểm chứng: 303 unit/MockMvc và 26 PostgreSQL 18.6 đều đạt, 0 skip. Integration kiểm filter/count/page/wildcard/scope/null, xóa đủ metadata đúng issue, giữ dữ liệu khác, rollback sau lỗi audit, chặn con/attachment, migration lặp và số không tái dùng sau xóa. Log `outputs/regression/issue-query-unit.log` và `issue-query-postgres-final.log`. Agent chính đã tự kiểm tra quyền, predicate, thứ tự ghi/FK và transaction; không dùng sub-agent.

Chưa có FE/E2E, benchmark hoặc mọi tình huống ghi phụ đồng thời với delete. Các API phụ chưa dùng chung khóa có thể gây xung đột FK; DB làm rollback thay vì để dữ liệu mồ côi. Không tự retry xóa.
