# Sửa/xóa sprint, board và cột

Tất cả route dùng Bearer JWT. Các mutation lấy project từ sprint/board/cột, kiểm quyền rồi khóa project và refresh trước ghi.

| Method/route | Body | Quyền/kết quả |
|---|---|---|
| PATCH `/api/sprints/{id}` | sprintName, goal, startDate, endDate tùy chọn | MANAGE_SPRINT; 200 SprintResponse |
| DELETE `/api/sprints/{id}` | Không có | MANAGE_SPRINT; 204; chỉ FUTURE |
| PATCH `/api/boards/{id}` | `{"boardName":"Team"}` | MANAGE_BOARD; 204 |
| DELETE `/api/boards/{id}` | Không có | MANAGE_BOARD; 204 |
| PATCH `/api/boards/columns/{id}` | `{"columnName":"Doing","wipLimit":3}`; trường tùy chọn | MANAGE_BOARD; 204 |
| DELETE `/api/boards/columns/{id}` | Không có | MANAGE_BOARD; 204; cột rỗng theo mapping |

Sprint FUTURE được sửa ngày; hai ngày sau khi ghép với dữ liệu cũ phải bắt đầu trước kết thúc. ACTIVE chỉ sửa tên/goal, đổi ngày trả 409. CLOSED không sửa/xóa. Null là giữ nguyên, goal rỗng xóa nội dung goal. Xóa FUTURE gỡ liên kết và reset rank product cho việc được trả lại, giữ issue và lịch sử sprint CLOSED khác.

Xóa board chỉ xóa rank/mapping/cột của board đó; không xóa issue, status hay project. Xóa cột còn issue theo mapping trả 409, cần chuyển việc trước. Cột không còn issue được xóa kèm mapping/rank cũ. WIP=0 không giới hạn; WIP dương mới thấp hơn số issue thật trả 409 trước khi sửa bất kỳ trường nào. Tên 1..100 ký tự, WIP âm 400; ID không tồn tại 404, thiếu quyền 403.

Khóa project dùng chung với move và status/backlog. Tên/goal chưa có revision client; hai cập nhật hợp lệ có thể lần lượt thành công, commit sau là kết quả. Không thêm migration trong đợt này.

Kiểm chứng: 319 unit/MockMvc + 30 PostgreSQL 18.6, 0 failure/error/skip. Test DB phát hiện bulk delete link vẫn còn entity managed khiến MapsId persist lại sprint; đã sửa bằng flush rank trước và detach link đã xóa trước xóa sprint. Retest kiểm sprint thật sự mất, việc về backlog, lịch sử CLOSED giữ nguyên. Test khác kiểm scope board, rollback sau lỗi delete, WIP và cột rỗng. Log `outputs/regression/containers-unit-final.log`, `containers-postgres-final.log`. Agent chính tự kiểm tra luồng quyền/khóa/FK/transaction, không dùng sub-agent. Chưa kiểm frontend, tải lớn hay mọi interleaving.
