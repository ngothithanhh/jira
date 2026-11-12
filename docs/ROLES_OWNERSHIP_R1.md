# Role R1 và quyền sở hữu project P1

## Hành vi

- Tài khoản đã xác thực và còn hoạt động được CREATE_PROJECT dù không có assignment global.
- Tạo project ghi projects.lead_user_id = actor JWT và project_members(actor, project, PROJECT_MANAGER), audit CREATE_PROJECT trong cùng transaction. Response memberCount=1.
- Chủ/lead quản lý project mình sở hữu. Thay role membership không tự chuyển lead/ownership.
- Chỉ SYSTEM_ADMIN trong user_role_assignments có quyền quản trị toàn hệ thống và vượt phạm vi project.
- Assignment global legacy của role khác giữ nguyên dữ liệu, không cấp quyền toàn hệ thống. Admin được gỡ; không được gán thêm.
- Thêm thành viên hiện là thêm trực tiếp tài khoản đã đăng ký, chọn role dự án; không có email invite/chờ chấp nhận. Giữ tối đa một PROJECT_MANAGER/project.
- Chủ hoặc SYSTEM_ADMIN có thể xóa project chưa có dữ liệu phụ thuộc và không có thành viên khác lead. Sole lead membership được dọn cùng transaction. FK lỗi rollback cả membership/project; không cascade dữ liệu công việc.

## API

| API | Quyền / kết quả |
| --- | --- |
| GET /api/admin/roles | SYSTEM_ADMIN; role, roleName, scope, reservedName, editablePermissions, globalAssignmentCount, projectAssignmentCount |
| GET /api/user-role | Giữ DTO cũ role/roleName để chọn role dự án |
| POST /api/user-role | Admin; body roleName, chuẩn hóa trim/uppercase;201 |
| PUT /api/user-role/{roleId} | Admin; đổi tên custom, giữ ID/liên kết;200 |
| DELETE /api/user-role/{roleId} | Admin; role chưa gán, dọn role_permissions của chính role;204 |
| GET /api/permissions | Danh mục mã thật; canonical trùng409, không tự merge |
| POST/PUT/DELETE /api/permissions | API legacy có guard enum/canonical; không có UI CRUD mã quyền |
| PUT /api/admin/users/{userId}/roles/{roleId} | Chỉ cấp SYSTEM_ADMIN global; role khác400 |
| POST /api/roles/{roleId}/permissions?permissionId=... | Legacy admin, không cấp mã global-only/unknown cho role dự án |
| DELETE /api/roles/{roleId}/permissions?permissionId=... | Legacy admin, gỡ đúng cặp; SYSTEM_ADMIN409 |
| POST /api/projects/create | User hoạt động; creator lead+PM; giữ HTTP200 hiện có |
| DELETE /api/projects/{projectId} | Chủ/SYSTEM_ADMIN; giữ HTTP200 hiện có, dependency409 |

Tên role mới: 1–50 ký tự, [A-Z][A-Z0-9_]*. SYSTEM_ADMIN, PROJECT_MANAGER, SCRUM_MASTER, DEVELOPER không được tạo bản sao/đổi tên/xóa.
Role thiếu404; tên trùng/role đang được dùng409; tên sai400; không admin403. Đổi tên cùng mã hiện tại là no-op, không audit giả.
Permission cố định theo PermissionName, bản ghi lạ không tạo capability. CREATE_PROJECT là khả năng chung, DELETE_PROJECT theo ownership; hai mã đó không được cấp qua ma trận role dự án.

## Transaction và migration

AdminRoleLock kiểm admin → khóa SYSTEM_ADMIN theo ID → khóa/refresh actor và recheck enabled/admin → khóa/refresh target role. Dùng cùng thứ tự với AccountManagementService.
Writer project member không phải tất cả dùng cùng khóa role; FK vẫn bảo vệ delete–assign. Không tuyên bố mọi race đã được serialize.
CREATE_ROLE/UPDATE_ROLE/DELETE_ROLE/GRANT_ROLE_PERMISSION/REVOKE_ROLE_PERMISSION dùng EntityType.ROLE với đúng role_id và cùng transaction nghiệp vụ.
Migration mới scripts/migrations/010_role_permission_audit.sql mở rộng audit check, giữ các loại entity cũ. Chạy scripts/migrate.ps1 theo hướng dẫn hiện có, backup trước áp dụng DB đang dùng; không sửa000..009.
Không tự backfill membership cho project cũ hoặc xóa/chuyển assignment global legacy. Lead cũ vẫn được quản lý project của mình.

## Kiểm chứng

Clean verify:416 unit test +82 integration test (PostgresRegressionIT79, SystemSmokeIT3), không lỗi/skipped. FE148 test/build và browser R1/P1, users, configuration cùng8 suite hồi quy đều qua. Review độc lập không còn finding mở.

- Unit test quyền global/project, guard role/permission, thứ tự khóa/recheck actor, CRUD và hồi quy backend.
- PostgreSQL cluster riêng: migration010 lặp, role/grants/counts, duplicate permission canonical, role đang dùng, concurrent role create2admin, concurrent project create, rollback project/membership khi FK chặn xóa.
- SystemSmokeIT HTTP thật: user không global role tạo/nhận PM, outsider legacy global DEVELOPER bị403, thêm thành viên/xóa rỗng, admin role CRUD và guard direct API.
- Build/test frontend và browser end-to-end: xem tài liệu ROLES_PERMISSIONS_CONNECTED.md ở repo FE.
- Không có cam kết bao phủ mọi lịch chạy đồng thời. Test khóa/recheck bằng mock không thay thế chứng minh thời điểm chờ DB; chưa ép lịch delete–assign ở mọi writer.
- R2 ma trận quyền nguyên tử, chống ghi đè và sửa seed giữ tập quyền qua restart chưa triển khai. Seeder hiện vẫn bổ sung các grant mặc định khi khởi động; không dùng API legacy để nghiệm thu tính lưu cấu hình qua restart.
