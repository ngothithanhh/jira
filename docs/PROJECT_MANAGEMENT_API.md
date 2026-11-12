# Project API — kết nối giao diện phần 2A

Mọi API nghiệp vụ dùng JWT và kiểm quyền trong service. ID trong path là projects.project_id; lead lấy từ users theo JWT khi tạo, không từ form.

| Method/path | Quyền | Input/kết quả |
|---|---|---|
| GET /api/projects/getList | VIEW_PROJECT trên từng project hoặc SYSTEM_ADMIN | Trả list đã lọc, không trả project người ngoài. |
| GET /api/projects/{id} | VIEW_PROJECT | Không có404; không được xem403. |
| POST /api/projects/create | CREATE_PROJECT toàn cục | projectName bắt buộc tối đa100, projectKey bắt buộc tối đa10; description nullable tối đa255. Trả200. |
| PATCH /api/projects/{id} | UPDATE_PROJECT trên project | projectName + description; giữ key/lead, trả200. |
| DELETE /api/projects/{id} | DELETE_PROJECT toàn cục | Thành công200 rỗng; FK còn sử dụng409; không xóa dây chuyền dữ liệu nghiệp vụ. |

ProjectSummary bổ sung additive issueCount/memberCount/sprintCount/activeSprintName. ProjectStatsRepository chạy bốn query theo tập IDs sau lọc quyền; count từ issues/project_members/sprints và ACTIVE name. Lead không có project_members không tăng memberCount; project mới rỗng có0. Legacy nhiều ACTIVE hiện đủ tên; không chọn ngẫu nhiên che dữ liệu lỗi. Không ghi bảng khi GET/statistics.

Create/update kiểm trim tên/key và giới hạn cột, không đổi case/regex key legacy. Trùng key trong service hoặc unique DB409; input blank/vượt cột400. Response giữ các field tên/ID/leader cũ. Không thêm role/template/board hay cấu hình khi tạo dự án rỗng.

Create/update/delete có transaction. Delete flush trước audit, audit cùng transaction; lỗi FK hoặc audit rollback xóa. project_issue_types giữ cascade cấu hình cũ; không thêm cascade issue/member/sprint/board. Chưa thêm optimistic version cho project metadata; hai update hợp lệ có thể theo commit sau.

Không có migration mới trong phần2A. DB hiện hữu chạy validate như trước. Unit/MockMVC mới kiểm validation/permission/count scope; browser/HTTP thật phần này đối chiếu counts với API nguồn và kiểm create/update/F5/delete/403/409. Không suy test unit là đã chạy DB hoặc mọi interleaving.

FE cùng nhóm ở docs/CONNECTED_PROJECTS.md của jira_fe. Notification/report do máy kia nhận; không đổi contract của các API đó.
