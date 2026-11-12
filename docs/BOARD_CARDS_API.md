# Card và kéo issue trên board

Bearer JWT bắt buộc. Route GET `/api/boards/{boardId}/issues` cần VIEW_PROJECT trên project của board, trả danh sách:

```json
[{"issue":{"issueId":5,"version":3},"columnId":60,"position":0,"version":3}]
```

`issue` là IssueResponse đầy đủ. `columnId:null` khi status chưa map. GET lấy toàn bộ issue của project, chưa tự giới hạn ACTIVE sprint. Client có thể đối chiếu danh sách sprint backlog để hiển thị sprint mong muốn.

PUT `/api/boards/{boardId}/issues/{issueId}/move`, cần EDIT_ISSUE:

```json
{"columnId":60,"statusId":2,"position":0,"expectedVersion":3}
```

Vị trí là chỉ số chèn 0-based trong cột đích sau khi loại chính card đang kéo. statusId phải map vào cột đích; API không tự đoán khi cột có nhiều status. Thành công trả một BoardCardResponse có version mới; giữ token mới cho lần kéo tiếp theo.

Màn Scrum connected gửi thêm `sprintId` nguyên dương trong body move. Sau kiểm quyền/khóa project/refresh và so version, backend đọc lại sprint: khác project400, thiếu404, không ACTIVE hoặc không còn cặp sprint/issue409, dừng trước ghi status/rank. Close/remove sprint dùng cùng khóa project: bên đi trước commit rồi bên sau đọc trạng thái/link mới. Client cũ bỏ sprintId giữ hợp đồng board toàn project. Guard không biến board thành snapshot lịch sử hay giới hạn endpoint GET về một sprint.

FE chỉ hiện giao tập card và issue sprint đã chọn nhưng tính WIP trên cả project. Index DnD của sprint được quy đổi sang index cột toàn project sau bỏ chính card; card ẩn ngoài sprint giữ thứ tự tương đối. Reorder cùng cột giữ status hiện tại. Cột nhiều status đích dùng workflow để chọn hoặc hỏi người dùng, không đoán từ tên cột.

- 400: body/ID/vị trí sai, issue khác project, cột khác board hoặc status chưa map cột đích.
- 401/403: thiếu danh tính/quyền.
- 404: board/issue/cột/project không tồn tại.
- 409: version cũ, cột đầy WIP hoặc cấu hình legacy một status map nhiều cột trong cùng board. Client tải lại, không tự gửi lại version cũ.

Hai người kéo cùng issue từ version 3: khóa project cho lượt đầu ghi rồi tăng version. Lượt sau refresh dưới khóa thấy version mới và nhận 409; không ghi đè. Hai card tranh WIP=1 cũng đếm lại dưới cùng khóa và chỉ một lượt vào được. Khóa này cũng dùng cho status/backlog/sprint mutation; chưa benchmark throughput.

Cột lấy từ status hiện tại, không lấy cột cũ trong board_issues. BoardIssue chỉ giữ thứ tự thủ công; nếu mapping/status đổi thì rank cột cũ bị bỏ qua. Vị trí trả về được chuẩn hóa từ 0 trong từng cột. Mọi status-change dùng lại workflow, lịch sử và notification; lỗi rollback cùng rank. Tạo issue và đổi status trực tiếp cũng kiểm WIP trên các board cùng project. WIP=0 là không giới hạn. Thay mapping có thể khiến dữ liệu hiện hữu vượt WIP; API không tự chuyển/xóa issue để sửa dữ liệu đó.

Không thêm bảng/cột mới trong đợt này; dùng board_issues hiện có. IssueResponse bổ sung version. Các mutation issue flush trước mapper để trả concurrency token mới nhất của transaction.

Kiểm chứng bổ sung 12/11/2026: 387 unit backend và 55 integration PostgreSQL riêng đạt (0 fail/error/skip). Bốn case có latch/transaction thật kiểm move đứng trước hoặc sau complete/remove dưới khóa project; case cùng version/tranh WIP/rollback cũ vẫn đạt. Test liên quan: BoardCardRegressionTest, PostgresRegressionIT. Kết quả FE/browser và luồng người dùng tại jira_fe/docs/CONNECTED_SCRUM_BOARD.md. Không coi kết quả này là benchmark hoặc bảo đảm mọi interleaving.

Kiểm chứng ngày 11/11/2026: 281 unit/MockMvc và 20 integration PostgreSQL 18.6 đều đạt, 0 skip. Integration kiểm cùng card/version, tranh WIP, API status trực tiếp, rollback notification, reorder cùng cột tăng version và mapping độc quyền. Log `outputs/regression/board-unit.log`, `board-postgres-final.log`. Hai test đồng thời kiểm kết quả commit, không chứng minh mọi interleaving hoặc tải lớn. Chưa có FE/E2E và dịch vụ notification bên ngoài trong checkout.

Agent chính đã tự đọc lại caller/service/repository, kiểm quyền trước đọc, thứ tự project-lock/refresh/version, nhánh lỗi/rollback và phạm vi board khi ghi rank. Không dùng sub-agent.
