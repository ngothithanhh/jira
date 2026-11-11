# Kết quả kiểm chứng cuối — 12/11/2026

## Kết quả thực thi

Lệnh scripts/test-postgres.ps1 -WithUnit chạy Maven clean verify với JDK 17.0.20.1, PostgreSQL 18.6 trên cluster mới loopback:55432 và schema smoke riêng cho full app.

| Nhóm | Số test | Failure | Error | Skip |
|---|---:|---:|---:|---:|
| Unit/MockMvc | 372 | 0 | 0 | 0 |
| PostgreSQL repository/service integration | 49 | 0 | 0 | 0 |
| Full Spring Boot HTTP smoke | 2 | 0 | 0 | 0 |
| Tổng | 423 | 0 | 0 | 0 |

BUILD SUCCESS lúc 00:39 ngày 12/11/2026 (Asia/Saigon), tổng thời gian Maven 1 phút 27 giây. Log workspace: outputs/regression/final-clean-verify.log. Báo cáo XML tại target/surefire-reports và target/failsafe-reports. Cluster test đã được dừng sau khi Maven kết thúc; không chạy DDL trên DB jira của người dùng.

Unit/MockMvc không được dùng thay cho bằng chứng DB. PostgreSQL integration mock ProjectSecurity/NotificationService cho các nhánh cần chủ động gây lỗi. Hai smoke test dùng HTTP thật qua Tomcat, SecurityConfig/JWT/UserDetails/AuthenticationManager/GlobalSecurity/ProjectSecurity và toàn bộ service/repository/notification DB thật; không mock các tầng đó.

## Lỗi phát hiện và sửa khi kiểm full app

- springdoc 2.5.0 gây NoSuchMethodError ControllerAdviceBean khi sinh /v3/api-docs trên Spring 6.2. Nâng 2.10.0 theo [bảng tương thích chính thức](https://springdoc.org/v2/#_what_is_the_compatibility_matrix_of_springdoc_openapi_with_spring_boot), retest endpoint trả 200, có bearerAuth và các route mới.
- IssueTypeResponse thiếu ID khiến caller không lấy được ID loại vừa tạo để gán vào project/tạo issue. Bổ sung issueTypeId giữ nguyên typeName, unit mapper và HTTP smoke lấy ID từ JSON thật.
- Giữ sửa PostgreSQL AuditLog.description dùng TEXT thay NVARCHAR(MAX) có sẵn trong workspace và đưa vào commit cuối, để clone từ Git tạo schema audit giống môi trường đã test.
- Cấu hình của DataJpaTest đổi thành TestConfiguration để không lọt vào component scan full app. Smoke tắt Open EntityManager In View, các luồng được kiểm vẫn map response được trong transaction phù hợp.

## Các kịch bản đáng chú ý

- Cùng tạo issue trong project không trùng key; counter không tái dùng số sau xóa.
- Refresh cùng token không tạo hai child còn hiệu lực; refresh tranh password/disable không thoát thu hồi phiên.
- Hai admin tự gỡ quyền/disable cùng lúc vẫn còn một admin enabled, cả khi legacy có duplicate assignment theo tên role khác hoa/thường.
- Cùng kéo card/version và hai card tranh một chỗ WIP: chỉ một lượt thành công; stale request trả 409.
- Reorder backlog giữ scope dưới project lock; status/membership mutation chờ khóa.
- Start/complete sprint và snapshot report cùng transaction; velocity đã chốt không đổi sau sửa/xóa issue.
- Xóa sprint nháp thực sự xóa row và trả issue về backlog, giữ lịch sử CLOSED; lỗi xóa board rollback metadata.
- Hai sửa comment cùng version chỉ một được commit; tạo comment tranh delete issue không để row mồ côi.
- Inbox chỉ thao tác người nhận hiện tại; audit có nullable actor, count/page/filter đúng và chặn non-admin.
- Migration 001..006 được kiểm riêng chạy lặp và các ràng buộc/backfill liên quan. Không coi đó là đã migrate DB triển khai.

## Tự kiểm tra theo 10 tiêu chí

| Tiêu chí | Kết luận trong phạm vi đã triển khai |
|---|---|
| Correctness | Đối chiếu caller/controller/service/repository và thứ tự validation/quyền/khóa/refresh/ghi/rollback. Giữ test tái hiện bug, thêm DB và full HTTP; không khẳng định hết mọi bug. |
| Readability | Luồng chính có comment tiếng Việt giải thích ID, nguồn dữ liệu, nhánh lỗi và mục đích khóa/snapshot. |
| Maintainability | Dùng chung security, mapper, workflow và WIP policy; không tạo thuật toán chuyển status thứ hai cho board. |
| Performance | Có index audit/inbox/event và phân trang tại DB. Khóa project/capture toàn sprint có chi phí; chưa benchmark. |
| Security | Global admin khác role project; ownership inbox/comment, auth_version/disabled và last-admin có test thật. Không gọi dịch vụ ngoài để kiểm quyền. |
| Error handling | Transaction/409/version/FK và lỗi giữa bước được kiểm; không tự retry mutation. API cũ ngoài phạm vi còn RuntimeException/validation chưa đồng nhất. |
| Testing | 423 case thực thi, 0 skip; có DB concurrency và full HTTP. Không có FE/browser E2E hoặc mọi interleaving. |
| Coding standards | Java 17/Spring theo repo, comment tiếng Việt; agent chính tự làm theo chỉ dẫn bỏ sub-agent. Không có generated/Mushroom cần sửa. |
| Architecture | Controller giữ mapping/validation, service giữ nghiệp vụ/transaction, repository giữ query/lock; report dùng snapshot có nguồn rõ ràng. |
| Documentation | Có plan/API từng đợt, FEATURE_STATUS.md đối chiếu 19 UC, RUN_AND_MIGRATE.md và cấu hình mẫu dùng env; secret/cache không commit. |

Không có review độc lập trong các đợt này theo chỉ dẫn mới nhất của người dùng. Agent chính tự kiểm tra và retest, không tự nhận đã được sub-agent review.

## Coverage và giới hạn

JaCoCo report ở phase test trước integration, vì vậy số dưới đây là coverage lượt unit/MockMvc, không phải tổng mọi bước HTTP/DB sau đó:

| Package | Dòng chạy/tổng |
|---|---:|
| service.impl | 1083/1250 |
| security | 76/93 |
| security.jwt | 49/50 |
| mapper | 157/174 |

Coverage không chứng minh đủ mọi nhánh. Chưa kiểm Cloudinary/SMTP/Redis thật, FE, load/benchmark hoặc mọi lịch chạy đồng thời. Những mở rộng ngoài tám đợt và các giới hạn từng UC được ghi trong FEATURE_STATUS.md. Chưa áp dụng migration lên DB triển khai và chưa push Git remote.
