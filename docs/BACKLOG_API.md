# API backlog

Tất cả route dùng `Authorization: Bearer <access-token>`. Danh sách trả các IssueResponse hiện có theo thứ tự hiển thị.

| Method | Route | Quyền |
|---|---|---|
| GET | `/api/projects/{projectId}/backlog` | VIEW_PROJECT |
| PUT | `/api/projects/{projectId}/backlog/order` | EDIT_ISSUE hoặc MANAGE_SPRINT |
| GET | `/api/sprints/{sprintId}/issues` | VIEW_PROJECT trên project của sprint |
| PUT | `/api/sprints/{sprintId}/issues/order` | MANAGE_SPRINT; FUTURE/ACTIVE |

PUT body: `{"issueIds":[9,5,12]}`. Đây là **toàn bộ** ID issues hiện thuộc backlog, theo thứ tự mới; không phải issue_number. `[]` chỉ thành công khi backlog rỗng. PUT trả lại danh sách đã sắp xếp, không cần gọi GET để biết kết quả.

- 400: body thiếu/null, ID không dương hoặc trùng.
- 401/403: thiếu xác thực/quyền trên đúng project.
- 404: project/sprint không tồn tại.
- 409: sprint đã đóng, scope khác danh sách hiện tại hoặc dữ liệu sprint legacy chéo project. Client tải lại rồi cho người dùng sắp xếp lại; không tự gửi lại snapshot cũ.

Product backlog gồm issue chưa DONE theo category và không thuộc FUTURE/ACTIVE sprint. Issue có status null vẫn được liệt kê; tên trạng thái tùy chỉnh không đổi nghĩa category. Link CLOSED là lịch sử, không loại issue khỏi backlog. Sprint backlog giữ toàn bộ issue của sprint, kể cả DONE và CLOSED để xem lịch sử.

Rank đã chốt đứng trước; việc chưa có rank xếp theo HIGHEST, HIGH, MEDIUM, LOW, LOWEST, rồi priority khác/null. Khi hòa thì dùng issue_number và issue_id. Thêm/gỡ/chuyển sprint reset rank product của việc liên quan; rank sprint mới là null. Không thay đổi added_at khi chỉ reorder.

PUT khóa project để scope không đổi giữa kiểm tra và ghi do API membership/status chạy đồng thời. Hai request chỉ đổi thứ tự với cùng một tập issue vẫn có thể lần lượt thành công; thứ tự commit sau cùng là kết quả. API hiện chưa có revision của toàn backlog để phát hiện ý định sắp xếp cũ. Khóa project làm giảm throughput ghi trong cùng project; chưa benchmark tải.

Trước deploy, chạy `scripts/migrations/002_backlog_order.sql` với `psql -v ON_ERROR_STOP=1` trên DB triển khai đã sao lưu. Migration chỉ thêm cột nullable và chạy lặp được; không phụ thuộc Hibernate tự update schema. Phiên này chỉ chạy migration trên DB test riêng.

Kiểm chứng: `BacklogRegressionTest`, các case MockMvc trong `ApiRegressionTest` và nhóm backlog mới trong `PostgresRegressionIT`. PostgreSQL kiểm mapping null/DONE/history, lưu rank, rollback sau flush và mutation chờ khóa reorder. Không có FE trong checkout để kiểm thao tác kéo thả trên trình duyệt.
