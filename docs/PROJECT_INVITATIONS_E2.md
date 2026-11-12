# E2 — Lời mời tham gia dự án

Người có quyền ADD_MEMBER tạo lời mời qua email. Đúng tài khoản đã xác minh email mới có thể chấp nhận hoặc từ chối. Membership, audit và thông báo cho người mời được ghi cùng transaction khi chấp nhận.

`POST /api/projects/{id}/invitations` nhận `{email, roleId}` và trả `201`. Đường cũ `POST /api/projects/{id}/members` nhận `{userId, roleId}` và trả `202` cùng DTO lời mời chờ. Tài khoản chưa đăng ký có thể nhận link và đi qua E1. Đăng nhập hoặc mở link chỉ hiển thị lời mời. Resend xoay proof và hủy job cũ chưa gửi. Thay đổi quyền người mời được kiểm lại tại thời điểm accept.

## Migration

Sao lưu và xác nhận database trước khi áp migration. Với database có ledger, chạy từ root BE trên PowerShell 7.

```powershell
pwsh -NoProfile -File .\scripts\migrate.ps1 -Database $DbName -HostName 127.0.0.1 -Port $DbPort -UserName $DbUser
```

Migration `013_project_invitations.sql` yêu cầu `011` và `012`. Bảng lời mời giữ tham chiếu project và role bằng FK RESTRICT. Một lời mời còn trong lịch sử có thể khiến thao tác xóa project hoặc custom role trả 409.

Đặt `APP_PUBLIC_BASE_URL` bằng origin frontend mà người nhận mở được. Dùng HTTPS trên môi trường triển khai và HTTP loopback khi nghiệm thu local. BE cần cấu hình [Brevo](BREVO_MAIL_OUTBOX.md) và khóa HMAC từ [E1](EMAIL_VERIFICATION_E1.md). Khi tạo hoặc gửi lại, mail bắt đầu ở trạng thái chờ. Sau khi Brevo nhận request, kiểm Inbox hoặc Spam để xác nhận thư đã đến.

## Nghiệm thu

1. Đăng nhập bằng tài khoản người mời, mở một dự án của mình, vào Thành viên và bấm **Mời thành viên**. Nhập một email khác và chọn role.
2. Trong tab **Lời mời**, kiểm trạng thái chờ và tải lại để xem kết quả gửi. Danh sách Thành viên vẫn có số lượng cũ.
3. Mở link email trong một browser context khác. Đăng nhập đúng email hoặc đăng ký và xác minh OTP nếu chưa có tài khoản. Kiểm trang dự án trực tiếp bị từ chối trước accept.
4. Bấm **Chấp nhận**, mở dự án, rồi tải lại trang Thành viên bên người mời. Kiểm thêm đúng một thành viên với role đã mời.
5. Với lời mời khác, thử **Gửi lại** sau 60 giây và mở link cũ. Thử **Thu hồi** hoặc **Từ chối** và kiểm số thành viên.

`EmailVerificationIT` chạy trên PostgreSQL thật, khởi động fake Brevo loopback và dùng cấu hình frontend như trong [runbook E1](EMAIL_VERIFICATION_E1.md). Browser dùng hai context để kiểm proof, actor, F5, kết quả không rõ sau mất kết nối và quyền truy cập trước/sau accept.
