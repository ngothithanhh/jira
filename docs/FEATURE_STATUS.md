# Đối chiếu 19 nhóm chức năng backend

Cập nhật ngày 12/11/2026. Đây là tình trạng mã nguồn và kiểm thử trong checkout backend; không phải xác nhận đã deploy hoặc đã hoàn thiện frontend.

| # | Nhóm chức năng đã chốt | Phạm vi hiện có | Bằng chứng/giới hạn chính |
|---|---|---|---|
| 1 | Quản lý tài khoản và xác thực | Register/login, JWT/refresh/logout, profile, đổi mật khẩu, thu hồi phiên theo auth_version | Auth/Account/Security tests, PostgreSQL auth thật và full HTTP smoke; chưa có quên mật khẩu qua email |
| 2 | Quản lý người dùng | Danh sách/chi tiết, profile, enabled/disabled, xóa khi không còn role/FK sử dụng | Account API và admin guard; giữ ít nhất một admin enabled |
| 3 | Quản lý project | Tạo/xem/sửa/xóa, lọc quyền đọc, lead, issue counter | Project tests và full HTTP create/read; xóa khi còn dữ liệu liên kết có thể 409, không tự xóa cây |
| 4 | Quản lý thành viên project | Thêm/gỡ/xem, đổi role, một PM, audit/notification trong transaction | PostgreSQL PM đồng thời/invitation rollback, HTTP thêm member; lead không gỡ trực tiếp |
| 5 | Quản lý issue | Tạo/chi tiết/sửa/assign/status/xóa, parent issue, version và key không tái dùng | Unit/DB/full HTTP; không tự xóa con/attachment, xóa vật lý có thể ảnh hưởng thống kê current |
| 6 | Quản lý comment và attachment | Comment CRUD/version/ownership, attachment upload/list/delete hiện có | Comment có DB/race/full HTTP; Cloudinary chỉ mock SDK, chính sách cũ xóa metadata kể cả remote delete lỗi chưa có retry/outbox |
| 7 | Quản lý issue type | CRUD chung, gán/gỡ/default theo project, response có issueTypeId | Configuration/mapper/full HTTP; cấu hình default legacy chưa được bảo đảm unique ở DB |
| 8 | Quản lý status và workflow | CRUD status, rule chuyển, allowed transition, chuyển status có lịch sử/notification | Configuration/Issue tests và HTTP move dùng workflow thật; chưa phải workflow tùy chỉnh nhiều scheme như Jira |
| 9 | Quản lý role và permission | CRUD role/permission, role-permission, global user-role và project member role | Admin toàn cục tách membership; last-admin/reserved role/race PostgreSQL |
| 10 | Quản lý Scrum board | Tạo/xem/đổi tên/xóa, card của project | Board tests và HTTP; GET cards lấy cả project, FE đối chiếu sprint backlog để giới hạn sprint |
| 11 | Quản lý column và mapping status | Thêm/đổi tên/WIP/xóa/reorder cột, map/unmap, chặn status trùng cột trong board | Unit/DB/HTTP; legacy mapping trùng trả 409, không tự sửa dữ liệu |
| 12 | Quản lý sprint | Tạo/xem/sửa nháp/start/complete/xóa FUTURE, một ACTIVE mỗi project | Unit/DB với project lock; ACTIVE không đổi ngày, CLOSED giữ lịch sử |
| 13 | Quản lý sprint backlog | GET, add/remove, order đầy đủ, chuyển unfinished khi complete | Backlog tests và DB rollback/race; không sửa CLOSED |
| 14 | Quản lý product backlog | Issue chưa DONE/chưa thuộc sprint mở, GET/reorder, rank nullable và priority fallback | Unit/DB; hai reorder cùng scope theo commit cuối, chưa có revision toàn backlog |
| 15 | Theo dõi và di chuyển issue trên board | Card suy từ status, move/reorder, workflow/WIP, expectedVersion | Cùng card/version và tranh WIP PostgreSQL + full HTTP stale move 409 |
| 16 | Tìm kiếm, lọc và phân trang issue | Keyword literal, status/type/assignee/reporter/sprint/priority/unassigned, sort whitelist | Query/count tại DB, scope project, unit/DB/full HTTP; size tối đa 100 |
| 17 | Notification | DB inbox, unread, mark one/all, delete đúng recipient | Unit/DB và full HTTP invitation/inbox; chưa có realtime push, email hoặc retry gửi ra ngoài |
| 18 | Báo cáo Scrum | Summary, event burndown, velocity snapshot START/OBSERVED/complete | DB snapshot/race/rollback và full HTTP; legacy chưa có lịch sử trả unavailable, chưa có chart FE hoặc bucket theo ngày |
| 19 | Quản trị hệ thống | Cấu hình quyền/type/status/workflow, global roles, account enabled, audit search/page và overview | Admin gate thật trong DB/HTTP, nullable actor; chưa phải monitoring/backup/operations console |

## Phạm vi đã hoàn thành trong đợt triển khai

Tám đợt trong IMPLEMENTATION_PROGRESS.md đã có code, test và commit riêng. Các gap chính đã được bổ sung: backlog có thứ tự, card/move/WIP và version, issue detail/delete/search/page, quản lý vòng đời sprint/board/cột, báo cáo có snapshot, tài khoản/global role/member role, comment edit/delete, inbox read-all/delete và audit/overview.

Full app smoke dùng SpringBootTest, cổng loopback ngẫu nhiên, PostgreSQL schema smoke riêng, SecurityConfig/UserDetails/AuthenticationManager/GlobalSecurity/ProjectSecurity/controller/service/repository thật. Nó kiểm đăng nhập, cấu hình/type/status/workflow, project/member, issue/backlog, sprint/board/move, comment, report, inbox, admin/outsider và JWT cũ sau đổi mật khẩu. Không mock notification DB hoặc quyền ở smoke. Nhóm DataJpaTest vẫn mock ProjectSecurity/NotificationService để chủ động tạo nhánh lỗi; hai loại bằng chứng khác nhau.

## Những phần không được coi là đã hoàn thiện

- Không có mã nguồn FE trong checkout, chưa kiểm tương tác trình duyệt hoặc giao diện biểu đồ.
- Chưa có kiểm chứng Cloudinary/SMTP/Redis thật, load benchmark hoặc mọi cách xen kẽ request đồng thời.
- Tính năng mở rộng như worklog API, release/version API, label/component, due date, hierarchy Epic riêng, bulk move, thông báo realtime và password recovery qua email chưa được bổ sung trong tám đợt. Entity của một số mục đã có nhưng không đồng nghĩa có API hoàn chỉnh.
- Migration triển khai chưa chạy trên DB người dùng trong đợt code/test. Script 001..006 đã có, cần backup và áp dụng đúng thứ tự trước chạy bản mới. Repo chưa có baseline SQL đầy đủ cho các bảng cũ; xem RUN_AND_MIGRATE.md.
- Một số API cũ vẫn dùng RuntimeException và validation chưa đồng nhất; chưa kết luận mọi input sai đều có lỗi HTTP chuẩn. Chưa mở rộng thành đợt chuẩn hóa tất cả API cũ.
- Project-level locks ưu tiên tính nhất quán, có thể giảm throughput ghi cùng project. Chưa benchmark để quyết định tối ưu khóa/query.

Tài liệu API từng phần và các giới hạn chi tiết nằm trong BACKLOG_API.md, BOARD_CARDS_API.md, ISSUE_QUERY_API.md, CONTAINER_MANAGEMENT_API.md, SCRUM_REPORT_API.md, ACCOUNT_MANAGEMENT_API.md và COLLABORATION_ADMIN_API.md.
