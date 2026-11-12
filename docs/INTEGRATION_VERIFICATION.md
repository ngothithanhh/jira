# Tiến độ tích hợp và kiểm chứng — 2026-11-13

Đã ghép các phần bàn giao của hai máy vào BE `feat/backend-integration` và FE `feat/backlog-ui-review`. Không ghép vào master/main.

| Nhóm | Backend nguồn | Frontend nguồn | Trạng thái code |
| --- | --- | --- | --- |
| Audit toàn hệ thống và riêng project | 89f5c78 | 5cad7d8 | Đã ghép |
| Tổng quan quản trị, sửa/xóa sprint, đổi thứ tự cột board | f848459 | 99faa98 | Đã ghép |
| Mail outbox, đăng ký/xác minh email, lời mời project | a817361 | 583b9d8 | Đã ghép |

## Các điểm đã xử lý khi ghép

- Reorder giữ token snapshot và khóa project, kiểm lại quyền sau chờ khóa; chỉ ghi một audit khi thứ tự thực sự đổi, cùng transaction. Token cũ hoặc scope sai vẫn bị chặn.
- Menu quản trị giữ Tổng quan, Người dùng, Vai trò & quyền, Loại công việc, Trạng thái, Workflow và Nhật ký.
- BFF giữ whitelist audit bên cạnh OTP/invitation; không đưa lại API đăng ký hoặc thêm thành viên trực tiếp theo hợp đồng cũ.
- Sửa test giao nhau: reorder dùng token; whitelist Tổng quan không coi audit là tuyến chưa nối; smoke đổi role của member có sẵn qua PUT role, luồng invitation kiểm riêng.
- Review độc lập phát hiện audit VERIFY_EMAIL đổi Instant thành giờ UTC không offset, khác quy ước audit cũ. Đã sửa riêng thời gian audit sang giờ JVM; không đổi Instant/UTC của TTL, trạng thái email hoặc giới hạn OTP.
- Thêm test UTC/Asia_Ho_Chi_Minh qua ranh giới ngày và assertion PostgreSQL kiểm thời gian audit so với thời điểm xác minh. Finding đã được review lại và đóng.
- Lệnh `npm run test:connected` đã bổ sung các bộ Tổng quan, quản lý sprint, OTP và invitation.

## Kết quả cuối trên bản ghép

| Kiểm tra | Kết quả |
| --- | --- |
| BE `clean verify` trên Java17 | Thành công, có đóng gói Spring Boot jar |
| BE unit | 441 pass, 0 fail/error/skip |
| PostgreSQL/HTTP integration | 117 pass, 0 fail/error/skip |
| FE connected qua script npm chuẩn | 150 pass |
| FE connected + planning + ui-demo | 174 pass |
| FE production build/TypeScript | Thành công |
| Chromium OTP và invitation | Thành công trong EmailVerificationIT |

Integration gồm MailOutboxPostgresIT7, EmailVerificationIT17, PostgresRegressionIT79, RolePermissionIT9, RolePermissionRestartIT1, SystemSmokeIT4. Runner tạo cluster PostgreSQL riêng trên loopback55432, xác minh đích trước DDL rồi dừng cluster khi kết thúc. Không dùng DB demo để chạy create-drop.

Chromium dùng bản Next production và backend thật trên PostgreSQL test, với Brevo adapter gọi HTTP collector giả lập local. Đã kiểm đăng ký/OTP sai/đúng, proof HttpOnly, reload/mất phản hồi, stale tab, xác minh tài khoản legacy, hai browser context mời/nhận, quyền trước/sau accept, thay actor trong lúc chờ và gỡ member đã nhận lời mời. Đây không phải bằng chứng gửi thư tới nhà cung cấp thật.

PG/HTTP suite kiểm thêm audit scope/count/filter/quyền/thu hồi quyền, snapshot sau xóa, rollback, cùng các luồng issue/backlog/board/sprint và R1/R2. Review chỉ đọc được thực hiện bởi một reviewer theo 10 tiêu chí AGENTS; không còn finding mở trong phạm vi tích hợp đã review. Không coi đây là chứng minh không còn mọi bug hoặc đã kiểm mọi interleaving.

## Runtime và phần cần cấu hình trên từng máy

- Migration mới là 011 mail outbox, 012 email verification, 013 invitations, 014 audit scope. Test đã chạy các migration liên quan trên schema test; lượt tích hợp này **không nâng schema DB demo đang dùng**.
- Cấu hình local, OAuth token Gmail/Brevo API key và các khóa mã hóa/HMAC không đi theo Git. Lượt này không bật sender hoặc gửi mail thật. Xem GMAIL_MAIL_OUTBOX.md, BREVO_MAIL_OUTBOX.md, EMAIL_VERIFICATION_E1.md và PROJECT_INVITATIONS_E2.md ở repo BE.
- Khi chạy bản mới trên DB demo, xác minh đúng DB, backup, áp migration bằng runner/ledger và khởi động BE với ddl-auto=validate. FE/BE cần cùng phiên bản OTP/invitation.
- Không tự đánh dấu user legacy đã xác minh chỉ để invitation hoạt động. User xác minh email qua trang Tài khoản rồi chấp nhận lời mời đúng email.
- Các màn `/ui-review` vẫn là bản mẫu; dùng các tuyến `/app` đã nối để kiểm sản phẩm thật.

## Luồng kiểm tra sau cấu hình runtime

1. SYSTEM_ADMIN → Quản trị → Tổng quan, Người dùng, Vai trò & quyền, Nhật ký; thử lọc project/entity/actor/date và mở chi tiết log.
2. Lead hoặc member có UPDATE_PROJECT → project → Nhật ký. VIEW-only không có link và direct API403. Thu hồi UPDATE_PROJECT rồi quay lại tab: dữ liệu log cũ bị chặn.
3. Backlog → sửa tên/mục tiêu/ngày sprint FUTURE; ACTIVE chỉ sửa trường được phép; xóa sprint FUTURE. Board → Cấu hình → đổi thứ tự cột → lưu → F5; snapshot cũ phải được yêu cầu tải lại.
4. Đăng ký → OTP → xác minh → đăng nhập thủ công. Tài khoản legacy → Tài khoản → xác minh email.
5. Project → Thành viên → Mời qua email/role → người nhận mở lời mời → chấp nhận hoặc từ chối. Chỉ sau accept thành công mới có membership; kiểm gửi lại/thu hồi và quyền người mời.

Các tệp credential, log test, backup, artifact build và cấu hình cá nhân được giữ ngoài Git. `AUDIT_LOG_HANDOFF.md` là mốc WIP ban đầu; dùng tài liệu này và `AUDIT_LOG.md` cho trạng thái mới.
