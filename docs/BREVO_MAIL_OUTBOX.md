# Brevo và mail outbox E0

Backend có thể chọn `app.mail.provider=brevo` để gửi email giao dịch qua [Brevo API](https://developers.brevo.com/reference/send-transac-email). Adapter gửi nội dung UTF-8 dạng plain text. OTP và lời mời sẽ dùng chung `MailOutbox` khi các luồng đó được nối.

## Kiểm tự động

Từ root BE, chạy PowerShell 7 với JDK đã cài:

```powershell
.\scripts\test-postgres.ps1 -JavaHome 'C:/path/to/jdk' -WithUnit
```

`BrevoMailSenderTest` dùng HTTP server loopback để kiểm tra JSON, header xác thực, lease, `401`, `403`, `429`, phản hồi không chắc chắn và timeout khi đọc body. `MailOutboxPostgresIT` chạy trên PostgreSQL thật, kiểm tra job bị `429` được lên lịch theo thời gian reset rồi lưu `PROVIDER_ACCEPTED` và `messageId` sau lần gửi thành công. Job kết thúc sẽ xóa ciphertext và nonce.

## Kiểm Brevo live

1. Đăng ký và xác minh sender trong Brevo. Dùng API key mới dành cho ứng dụng. [Thiết lập email giao dịch](https://developers.brevo.com/docs/send-a-transactional-email) · [Quản lý API key](https://help.brevo.com/hc/en-us/articles/40159485019794-Create-and-manage-your-API-keys)
2. Từ PowerShell 7, đặt biến trong phiên chạy local và chạy CLI bằng JDK. CLI dùng cùng adapter với worker và chỉ gửi một thư về chính `BREVO_SENDER_EMAIL`.

   ```powershell
   $env:BREVO_API_KEY = Read-Host 'Brevo API key' -MaskInput
   $env:BREVO_SENDER_EMAIL = Read-Host 'Địa chỉ sender đã xác minh trong Brevo'
   $env:BREVO_SENDER_NAME = Read-Host 'Tên hiển thị người gửi'
   java '-Dloader.main=com.example.jira.mail.BrevoSelfTest' -cp target/jira-0.0.1-SNAPSHOT.jar `
     org.springframework.boot.loader.launch.PropertiesLauncher --send-to-self
   ```

3. `PROVIDER_ACCEPTED` cùng `messageId` xác nhận Brevo đã nhận yêu cầu. Đối chiếu `messageId` trong Brevo Transactional Logs, rồi mở Inbox hoặc Spam để xác nhận đã nhận thư. Brevo có thể thay địa chỉ **From** dùng miền `gmail.com` bằng địa chỉ do Brevo cấp. [Cách Brevo xử lý sender dùng miền miễn phí](https://help.brevo.com/hc/en-us/articles/14925263522578-Comply-with-Gmail-Yahoo-and-Microsoft-s-requirements-for-email-senders)

## Bật worker

Áp migration `011` trên DB đã xác minh theo [hướng dẫn migration E0](GMAIL_MAIL_OUTBOX.md#migration-và-cấu-hình). Với [cấu hình mẫu](application-example.properties), đặt `MAIL_PROVIDER=brevo`, `MAIL_ENABLED=true`, `MAIL_ENCRYPTION_KEY` và ba biến `BREVO_*` vào môi trường chạy BE. Giữ API key và khóa mã hóa trong cấu hình local ngoài Git.

Worker thử lại `429` theo backoff hiện có và tôn trọng `Retry-After` lẫn `x-sib-ratelimit-reset`. Nếu Brevo trả `401`, job hiện tại kết thúc `FAILED/BREVO_AUTH` và tiến trình ngừng lấy thêm job gửi. Cập nhật cấu hình và khởi động lại BE để xử lý hàng đợi còn hiệu lực. Timeout hoặc `5xx` có thể xảy ra sau khi Brevo đã nhận request, nên job chuyển `UNKNOWN` và không được thử lại tự động.
