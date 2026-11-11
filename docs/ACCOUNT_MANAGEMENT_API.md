# Tài khoản, trạng thái người dùng và vai trò

Bearer JWT bắt buộc; profile/password lấy ID user từ phiên, không nhận target ID. UserResponse thêm enabled, không trả hash/auth_version.

| Route | Body/quyền |
|---|---|
| PATCH `/api/account/profile` | userName/fullName tùy chọn; tên 1..50, fullName tối đa 100; trả UserResponse |
| POST `/api/account/password` | oldPassword/newPassword; mới ít nhất 8 ký tự, tối đa 72 byte UTF-8; 204 rồi đăng nhập lại |
| GET `/api/admin/users/{userId}/roles` | SYSTEM_ADMIN; role toàn cục |
| PUT/DELETE `/api/admin/users/{userId}/roles/{roleId}` | SYSTEM_ADMIN; gán/gỡ idempotent, 204 |
| PATCH `/api/admin/users/{userId}/enabled` | SYSTEM_ADMIN; `{"enabled":false}` hoặc true; 204 |
| PUT `/api/projects/{projectId}/members/{userId}/role` | ADD_MEMBER trên project; `{"roleId":7}`; 204 |

roleId là user_roles nhưng bảng gán khác nhau: user_role_assignments cho toàn cục, project_members cho dự án. Đổi role project không cấp quyền admin toàn cục. SYSTEM_ADMIN không được gán mới làm role project. Mỗi project chỉ có một PM; add/đổi PM cùng khóa project. Gỡ member không gỡ lead còn được projects chỉ định.

Đổi mật khẩu hoặc enabled tăng users.auth_version và thu hồi toàn bộ refresh trong cùng transaction/audit. JWT có claim ver, so với UserPrincipal tải từ DB mỗi request. Token cũ thiếu ver là 0; sau khi version tăng phải login lại. Re-enable cũng tăng version nên token trước lúc khóa không sống lại. Request đã bắt đầu có thể hoàn tất; token phát ra từ refresh đang chạy có thể bị vô hiệu hóa bởi mutation ngay sau đó.

Refresh/logout khóa user trước token; refresh đọc lại token sau khóa để không dùng cờ revoked cũ. Logout thu hồi cả family và idempotent nếu token đã mất. Login khóa user trước AuthenticationManager và mint JWT theo version vừa đọc. Các luồng public không dùng legacy helper verify/rotate rời transaction.

Gán/gỡ admin và đổi enabled dùng khóa chung trên các role SYSTEM_ADMIN, recheck actor sau khi chờ và giữ ít nhất một admin enabled. Đếm DISTINCT user, kể cả dữ liệu legacy có hai role admin khác hoa/thường cho cùng người. Role SYSTEM_ADMIN là cấu hình reserved, không tạo bản sao/đổi tên/xóa qua API role. Seeder vẫn tạo như trước.

Xóa user qua API cũ cần DELETE_USER và admin; phải gỡ global roles trước, FK dữ liệu đang dùng trả 409. Disable giữ lịch sử. Thiếu ID 404, thiếu quyền 403, body sai/mật khẩu cũ sai 400, last admin/PM trùng hoặc xóa khi còn liên kết 409.

Migration triển khai: `scripts/migrations/005_account_state.sql` sau backup, bằng `psql -v ON_ERROR_STOP=1`; additive, chạy lặp được. Phiên này chỉ áp dụng lên DB test. Không cấu hình SMTP hoặc quên mật khẩu qua email trong đợt này.

Kiểm chứng ngày 12/11/2026: `scripts/test-postgres.ps1 -WithUnit` từ clean đạt 354 unit/MockMvc + 43 PostgreSQL 18.6, 0 failure/error/skip. Authentication integration dùng DaoAuthenticationProvider/UserDetails/BCrypt thật; project permission vẫn mock. DB kiểm login mật khẩu mới, phiên bị thu hồi, admin cuối đồng thời, legacy duplicate role, refresh tranh password/disable, PM đồng thời, audit/invitation rollback và migration lặp. JWT filter thật trong MockMvc kiểm version cũ/disabled không tới controller. Log `outputs/regression/accounts-full-final.log`.

Agent chính tự kiểm tra thứ tự role→user→token và project lock, scope gán role, timestamp/audit và các nhánh lỗi; không dùng sub-agent. Chưa có FE/E2E, SMTP, load test hoặc mọi interleaving. GlobalSecurity giữ transaction đọc tên role lazy để không phụ thuộc Open EntityManager In View.
