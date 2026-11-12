# E1 — Xác minh email

E1 dùng Brevo và mail outbox E0. Đăng ký tạo challenge và job trong cùng transaction. Xác minh OTP mới tạo user và audit. Người dùng đăng nhập sau bước xác minh. Tài khoản legacy giữ email đang lưu, đăng nhập và xác minh email từ trang Tài khoản.

## Migration và cấu hình local

Xác nhận đúng máy chủ, database và người dùng trước khi sao lưu. Với database đã được quản lý bằng ledger migration:

```powershell
psql -X -h 127.0.0.1 -p 5432 -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c 'select current_database(), current_user, inet_server_port()'
pg_dump -h 127.0.0.1 -p 5432 -U $DbUser -d $DbName -Fc -f $BackupFile
pwsh -NoProfile -File .\scripts\migrate.ps1 -Database $DbName -HostName 127.0.0.1 -Port 5432 -UserName $DbUser
```

Dùng PostgreSQL client cùng phiên bản với server. Migration `012_email_verification.sql` chạy sau `011`. Nếu preflight báo các `users.user_id` trùng email chuẩn, đối chiếu dữ liệu rồi chạy lại. Với DB legacy chưa có ledger, xác minh schema và quy trình nhận quản lý ledger trước khi dùng script migration.

Đặt cấu hình BE từ [mẫu cấu hình](application-example.properties) và [runbook Brevo](BREVO_MAIL_OUTBOX.md). Sinh riêng `VERIFICATION_HMAC_KEY` và `MAIL_ENCRYPTION_KEY`, mỗi khóa là 32 byte ngẫu nhiên mã hóa Base64. Giữ khóa ngoài Git và giữ ổn định qua restart. Khi đổi HMAC key, proof cũ hết hiệu lực và hạn mức hiện có chuyển sang khóa mới. Sau migration, chạy BE và FE cùng phiên bản E1 vì response register đổi thành `202` challenge. FE trỏ `BACKEND_API_URL` đến đường dẫn `/api` của BE và đặt `APP_ORIGIN` đúng origin trình duyệt.

Địa chỉ mới dùng dạng ASCII cho phép domain punycode. PostgreSQL dùng collation `"C"` cho biểu thức chuẩn hóa để chữ hoa ASCII có cùng cách so sánh với Java. Email legacy dùng chuỗi lưu trong DB khi cấp JWT và gửi thư. `getRemoteAddr()` là nguồn IP của BE. Khi BE thấy IP chung của BFF thì dùng chung hạn mức IP, đồng thời vẫn đếm riêng theo email. Hạ tầng muốn nhận IP gốc cần cấu hình tầng HTTP chỉ tin proxy do hệ thống quản lý và phải loại bỏ header do client tự gửi.

## Nghiệm thu

1. Mở `/app/sign-up` và điền email có thể nhận thư. Bấm Tạo tài khoản, thấy màn OTP và email che bớt. Kiểm F5 và xem trạng thái giao thư. Kiểm Inbox hoặc Spam sau khi Brevo chấp nhận yêu cầu.
2. Nhập sai mã một lần, rồi nhập đúng mã. Sau `204`, đăng nhập bằng thông tin vừa đăng ký. Mở `/app/account` để thấy email đã xác minh.
3. Với email thử khác, chờ hết 60 giây rồi Gửi lại mã. Mã cũ bị từ chối. Từ một tab khác, bấm Sửa email và tạo lượt mới. Tab cũ sẽ báo lượt xác minh đã đổi nếu bấm Xác minh hoặc Gửi lại.
4. Với tài khoản legacy có inbox thử và `email_verified_at IS NULL`, đăng nhập, mở `/app/account`, bấm Gửi mã xác minh rồi nhập OTP. Tải lại và kiểm phiên đăng nhập vẫn sử dụng được.
5. Đăng ký lại bằng email đã tạo hoặc biến thể viết hoa của email đó. Kiểm phản hồi `409`, rồi đăng nhập với mật khẩu cũ.

## Kiểm tự động

`EmailVerificationIT` dựng Spring Boot thật trên PostgreSQL riêng, áp `011` và `012`, dùng Brevo HTTP giả lập chạy loopback. OTP trong test do ứng dụng tạo và collector giữ trong RAM. Bộ test kiểm register/verify/resend, race, rollback audit, legacy, trạng thái mail và duplicate preflight.

Để chạy cả Chromium, build FE trước, đặt `E1_FE_ROOT` trỏ root FE, `E1_NODE_EXE` trỏ Node và tùy chọn `PLAYWRIGHT_MODULE`, `CHROMIUM_PATH`, `NEXT_DIST_DIR`. Từ BE chạy `pwsh -NoProfile -File .\scripts\test-postgres.ps1 -JavaHome $JavaHome -WithUnit`. Test dựng Next production và Chromium trên loopback rồi dừng khi xong. Kiểm BFF mock riêng từ FE bằng `node --experimental-strip-types --test tests/connected-email-verification.test.mjs`.
