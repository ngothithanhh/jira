# API quản trị người dùng

GET /api/admin/users và GET /api/admin/users/{userId} chỉ cho SYSTEM_ADMIN. GET /api/user legacy cũng được kiểm SYSTEM_ADMIN; thêm thành viên project tiếp tục dùng candidate API có quyền project.

## Query và response

Danh sách nhận keyword<=100 ký tự, enabled=true|false tùy chọn, page>=0, size1..100 (mặc định20), offset không vượt int. Tên/email không phân biệt hoa/thường; ký tự %, _ và backslash được escape như nội dung literal. Sort createdAt DESC,userId DESC.

Response: items,page,size,totalItems,totalPages.
User row: userId,userName,fullName,email,enabled,createdAt,roles:[{role,roleName}],projectMembershipCount.
role là user_roles.role_id. projectMembershipCount đếm project_members của target; không suy từ quyền lead hoặc số issue.

AdminUserQueryService kiểm admin trước query, phân trang DB trước khi batch đọc roles/count cho IDs trong trang. UserRepository FETCH graph bỏ collection EAGER userRoleAssignments để số query không tăng từng user. Detail chỉ trả DTO hiển thị, thiếu404; không trả hash/token/authVersion. Không ghi audit cho GET.

## Mutation giữ nghiệp vụ hiện có

| Method | Route | Hành vi |
|---|---|---|
| PUT | /api/admin/users/{userId}/roles/{roleId} | Gán cặp user_role_assignments, đã có no-op204 |
| DELETE | Cùng route | Gỡ cặp, chưa có no-op204; chặn admin cuối |
| PATCH | /api/admin/users/{userId}/enabled | enabled boolean; đổi trạng thái, tăng auth_version, revoke refresh, audit |

AccountManagementService giữ thứ tự khóa role SYSTEM_ADMIN → actor → target, refresh actor/target và recheck quyền. Bảo vệ ít nhất một admin enabled bằng DB count distinct; mutation và audit cùng transaction. Không thay project_members/role_permissions hoặc xóa dữ liệu công việc.

Role toàn cục được kiểm theo DB mỗi request; thay role không tự tăng auth_version. Khóa/mở hoặc đổi password tăng version, các token cũ không sống lại khi mở khóa. Không có migration mới cho phần này.

## Kiểm chứng và giới hạn

Unit/API kiểm DTO, query validation, batch mapping và legacy user-list permission. PostgreSQL integration kiểm literal search, pagination/filter/count không nhân bản, global role tách membership và query count khi trang3vs12 user; SystemSmokeIT kiểm HTTP thật/admin/user403 và không lộ passwordHash. Last-admin/race/token revoke được hồi quy qua tests AccountManagement hiện có.

BFF và browser kiểm session self-disable/self-revoke/actor switch riêng ở FE. Mock unit hoặc query count test không phải bằng chứng benchmark production hay mọi lịch xen kẽ concurrent.
