# Gmail API và mail outbox E0

E0 cung cấp transport và hàng đợi nội bộ cho OTP đăng ký, xác minh email tài khoản và lời mời project. Môi trường mặc định tắt worker qua `MAIL_ENABLED=false`. Các đợt nghiệp vụ sẽ enqueue bằng `MailOutbox` trong transaction của chúng.

## Kiểm local

Từ root BE, chạy các lệnh sau với JDK đã cài. Script PostgreSQL tạo cluster riêng trên loopback, xác minh đích trước DDL và dừng đúng cluster khi xong.

```powershell
.\mvnw.cmd -B -ntp -Dtest=GmailApiMailSenderTest test
.\scripts\test-postgres.ps1 -JavaHome 'C:/path/to/jdk' -WithUnit
```

`GmailApiMailSenderTest` dùng HTTP server loopback và decode MIME. `MailOutboxPostgresIT` áp migration hai lượt, kiểm rollback enqueue, tranh lease, hủy generation, giới hạn retry và lease hết hạn. Một ca chạy từ PostgreSQL qua adapter tới fake Google và kiểm provider ID cùng trạng thái cuối trong DB. Hai nhóm test dùng credential và địa chỉ giả.

## Migration và cấu hình

Trên DB đích đã áp `000` đến `010`, xác minh tên DB và sao lưu trước khi chạy `011`. Dùng `psql` và `pg_dump` từ cùng PostgreSQL client. Ví dụ sau dành cho một DB test đã xác minh, với đường dẫn backup riêng ngoài Git.

```powershell
psql --dbname $VerifiedTestDatabase -X -v ON_ERROR_STOP=1 -c 'select current_database(), current_user'
pg_dump --dbname $VerifiedTestDatabase --format=custom --file $BackupFile
psql --dbname $VerifiedTestDatabase -X -v ON_ERROR_STOP=1 -f scripts/migrations/011_mail_outbox.sql
```

Đặt `MAIL_SENDER_ADDRESS`, `GMAIL_TOKEN_FILE`, `MAIL_ENCRYPTION_KEY` và tùy chọn `MAIL_SENDER_NAME` theo mẫu cấu hình. Khóa mã hóa là 32 byte ngẫu nhiên mã hóa Base64, dùng riêng cho outbox. Lưu biến nhạy cảm trong cấu hình local ngoài Git. Sau khi áp migration và xác minh OAuth, đặt `MAIL_ENABLED=true` để chạy worker. Trước khi thay khóa hoặc tăng `MAIL_KEY_VERSION`, chờ các job đang hoạt động kết thúc hoặc hết hạn. Bản này đọc một phiên bản khóa tại một thời điểm.

`MailOutbox.enqueue` nhận `kind`, UUID của aggregate, generation, nội dung và hạn gửi. `Optional.empty()` nghĩa là generation đã có job hoặc đã hết hạn. Nghiệp vụ gọi `supersedeThrough` cùng transaction khi đổi generation hoặc thu hồi. Job chưa dispatch chuyển `SUPERSEDED`. Job đang gửi được đánh dấu để ngừng lượt retry, còn kết quả đã nhận từ Google vẫn được ghi theo lease. OTP và link cũ phải được vô hiệu ở tầng nghiệp vụ khi đổi generation.

Worker chỉ retry từ chối do rate limit hoặc lỗi lấy access token trước khi gửi. Mỗi lượt kiểm hạn, backoff và tối đa bốn lần xử lý một job. `401` rõ ràng ở endpoint gửi cho phép làm mới access token và gửi lại một lần, sau khi kiểm lại lease. Timeout khi gửi, lỗi `5xx` và lease `SENDING` hết hạn kết thúc ở `UNKNOWN`. Trạng thái cuối xóa ciphertext và nonce. Metadata còn lại dùng để đối chiếu job. Access token dùng chung trong một tiến trình. Với `NEEDS_REAUTH`, cấp lại quyền cho sender rồi khởi động lại tiến trình với file mới. Giữ mặc định JDK HTTP client về retry method và timeout.

## Cấp quyền và kiểm Gmail live

Trong Google Cloud project được giao, kiểm Gmail API đã bật và dùng một OAuth client loại **Desktop app** dành cho công cụ bootstrap. Nếu consent còn ở chế độ External Testing, dùng tài khoản gửi đã được thêm vào test users. Hạn refresh token trong chế độ này là 7 ngày khi cấp scope Gmail.

Chạy PowerShell 7 trên Windows với tệp OAuth Desktop đã tải vào thư mục local riêng. Chọn đúng tài khoản Gmail tương ứng với `SenderAddress` khi trình duyệt mở consent. `login_hint` chỉ gợi ý tài khoản. Script dùng PKCE, `state` và callback loopback, rồi in đường dẫn của file OAuth có ACL giới hạn người dùng hiện tại.

```powershell
.\scripts\bootstrap-gmail-oauth.ps1 -DesktopClientJson $DesktopClientFile -SenderAddress $SenderAddress
$env:GMAIL_TOKEN_FILE = $PrivateTokenFile
$env:MAIL_SENDER_ADDRESS = $SenderAddress
java '-Dloader.main=com.example.jira.mail.GmailSelfTest' -cp target/jira-0.0.1-SNAPSHOT.jar `
  org.springframework.boot.loader.launch.PropertiesLauncher --send-to-self
```

CLI dùng adapter của ứng dụng, không mở HTTP server ứng dụng và chỉ gửi về chính địa chỉ sender đã khai báo. `PROVIDER_ACCEPTED` và provider ID xác nhận Gmail nhận request. Kiểm Inbox hoặc Spam và đối chiếu **From** cùng tài khoản nhận trước khi ghi nhận gửi live thành công. Giữ file Desktop client, file token và mật khẩu ở máy local.
