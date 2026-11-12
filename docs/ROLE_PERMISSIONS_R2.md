# Ma trận quyền R2

## Phạm vi và API

R2 bổ sung đọc/lưu tập quyền của một role dự án, giữ R1/P1 ownership và CRUD role.
Chỉ SYSTEM_ADMIN được mở API quản trị. Role DEVELOPER/PM/Scrum Master và custom dùng cho membership, không tạo quyền global.

| API | Hợp đồng |
| --- | --- |
| GET /api/admin/role-permissions | Snapshot role/count/grants và metadata danh mục theo batch |
| GET /api/roles/{roleId}/permissions | {role,roleName,permissionIds}, ID role thuộc user_roles |
| PUT /api/roles/{roleId}/permissions | {expectedPermissionIds,permissionIds} → snapshot mới200 |
| POST/DELETE cùng path | API cấp/gỡ grant lẻ cũ vẫn được khóa/guard, FE không dùng để giả lưu nguyên tử |

Hai arrays là permissions.permission_id, nguyên dương, không trùng, tối đa1000/array. Integer deserializer riêng từ chối chuỗi, boolean, số lẻ thay vì ép thành ID.
SYS409; thiếu role/desired permission404; mã không áp dụng/body sai400; không admin403; snapshot cũ hoặc canonical catalog trùng409.
So sánh set không theo thứ tự. expected phải bằng stored current trước mọi thay đổi. No-op hợp lệ không lưu/audit.
Không thêm migration hoặc thay đổi schema trong R2.

## Transaction và khả năng đồng thời

AdminRoleLock giữ thứ tự R1: kiểm admin → khóa các SYSTEM_ADMIN role theo ID → khóa/refresh actor users và recheck enabled/admin → khóa/refresh target role.
Writer đọc current grant sau khóa, kiểm toàn bộ desired, tính added/removed rồi delete đúng cặp cần gỡ và insert cặp cần thêm.
Flush và SET_ROLE_PERMISSIONS(EntityType.ROLE, đúng role_id) cùng transaction; lỗi audit/FK rollback cả tập.
Writer legacy POST/DELETE và CRUD role giữ khóa tương tự. Hai admin cùng expected nhưng desired khác: một lưu, lượt sau409.
So sánh nội dung set không phát hiện lịch sử A→B→A (ABA); không phải revision/version đầy đủ hoặc exactly-once. Không tự retry mutation.

Ma trận GET dùng REPEATABLE_READ để role/count/catalog/grants thuộc cùng snapshot. Truy vấn projection theo nhóm tránh N query từng role.
Grant legacy vẫn hiện stored nhưng không được thêm lại vào desired nếu không áp dụng; admin phải xác nhận gỡ trên UI.
CREATE_PROJECT là khả năng chung của tài khoản hoạt động; DELETE_PROJECT là ownership; DELETE_USER/CREATE_ISSUE_TYPE là quản trị toàn cục. Không cấp4 mã này qua role project.
VIEW_PROJECT luôn có ở membership; stored grant là dữ liệu độc lập. Lead/SYS bypass theo policy R1/P1.
Template role dùng chung cho mọi project tham chiếu role đó. Không sao chép grant thành một ma trận riêng mỗi project.

## Seeder

seedRole trả role và createdNow. Chỉ role vừa tạo được gán bộ grant mặc định; role đã có giữ tập cấu hình kể cả rỗng.
Permission enum thiếu vẫn được seed. Không vì grant rỗng mà cấp lại default sau restart.
Giữ bootstrap admin và legacy permission migration hiện có; không reset password, role tên cũ hoặc assignment global.
Role/grant thay đổi không tăng auth_version hoặc logout hàng loạt; request sau kiểm DB. Request đang chạy có thể đã dùng quyền tại thời điểm đọc.
Không thay auth/OTP/invitation hoặc schema của máy kia.

## Kiểm chứng và chạy

Kết quả thực thi cuối:427 unit backend,93 integration PostgreSQL/HTTP (baseline79 +RolePermissionIT9 +restart1 +HTTP4) pass, không skip. FE155 test/build pass. Browser matrix10 nhóm, R1 role/ownership10, users12, configuration15 và8 suite hồi quy pass. Review độc lập hoàn tất, finding reload actor đã sửa/kiểm lại, không còn finding mở.

- Unit: atomic delta/no-op/empty/stale/missing/global-only/SYS/invalid arrays/canonical duplicate/strict ID parsing.
- RolePermissionIT dùng cluster test preflight: audit failure rollback, hai admin cùng snapshot, legacy writer xen PUT, grant chỉ theo membership hai project, seed existing/empty, legacy cleanup, matrix snapshot khi writer commit giữa các query.
- RolePermissionRestartIT mở/đóng/mở lại toàn bộ Spring Boot trên schema r2_restart của DB test để kiểm DEVELOPER empty vẫn empty.
- SystemSmokeIT HTTP thật kiểm matrix/GET/PUT/stale400/409/strict IDs/SYS và hồi quy R1.
- Chạy scripts/test-postgres.ps1 -WithUnit với Java17 sau khi dừng đúng JAR đang khóa target; script chỉ create-drop DB test riêng, không dùng DB ứng dụng.
- Không tuyên bố bao phủ mọi lịch race hoặc benchmark tải production. Xem kết quả thực thi cuối trong bàn giao, không coi danh sách case là bằng chứng nếu chưa chạy.
