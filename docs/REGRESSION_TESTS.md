# Bộ kiểm thử hồi quy backend

## Phạm vi và giới hạn

Bộ test bao phủ các chức năng đang triển khai trong backend Spring Boot. Chưa có frontend trong checkout này.
Không viết test cho API chưa tồn tại: báo cáo Scrum, đổi/quên mật khẩu. Product/sprint backlog, board cards/move, chi tiết/xóa issue và tìm kiếm/phân trang đã có API và test; xem BACKLOG_API.md, BOARD_CARDS_API.md và ISSUE_QUERY_API.md.
Không coi coverage cao hoặc mọi test xanh là bằng chứng đã hết bug.

- Unit: JUnit 5, Mockito, AssertJ. Dùng SecurityContext thật với UserPrincipal giả lập.
- HTTP: MockMvc qua SecurityConfig, JwtAuthenticationFilter và JwtUtil thật. Service được mock để kiểm hợp đồng controller; kết quả này không chứng minh tính nguyên tử DB.
- Integration: JPA/repository và service thật trên PostgreSQL container riêng hoặc cluster test mới do script tạo. Giả lập quyền dự án và NotificationService để chủ động tạo nhánh lỗi. Không chứng minh phân quyền đầu cuối bằng nhóm integration này.
- Cloudinary: SDK và dịch vụ ngoài được mock. Không upload/xóa tệp trên tài khoản thật.
- Không gọi SMTP, Redis hoặc HTTP bên ngoài để làm dữ liệu test.

Test contextLoads cũ phụ thuộc cấu hình DB mặc định đã được thay bằng test HTTP và nhóm PostgreSQL tách biệt. Khi chuyển sang bộ test này lần đầu, chạy `clean` để xóa class contextLoads đã biên dịch từ trước.

## Chuẩn bị

Dùng JDK 17 như pom hiện tại. Trong PowerShell, đặt JAVA_HOME đến JDK 17 đã cài, ví dụ:

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-17.0.20.1'
```

Lần đầu Maven cần tải các dependency test. Mockito dùng `mock-maker-subclass` để không cần tự attach Java agent; bộ test không cần giả lập static/final. JaCoCo vẫn dùng agent khởi động cùng JVM.

## Cách chạy

Chạy toàn bộ unit và HTTP, bao gồm test tái hiện lỗi đã biết:

```powershell
.\mvnw.cmd clean test
```

Lệnh trên được phép đỏ khi code nghiệp vụ chưa sửa các bug được test phát hiện. Không đặt `testFailureIgnore` trong pom và không disable các test này.

Các bug đã sửa vẫn giữ test nhưng bỏ nhãn `known-bug`. Không dùng lọc nhãn để thay thế lượt hồi quy đầy đủ. Lệnh dưới đây chỉ dành cho chẩn đoán nếu về sau có thêm nhãn:

```powershell
.\mvnw.cmd test '-Dgroups=known-bug'
.\mvnw.cmd test '-DexcludedGroups=known-bug'
```

Lệnh loại trừ known-bug chỉ kiểm tra phần còn lại, không được dùng để kết luận toàn dự án đạt hồi quy.

Chạy PostgreSQL riêng sau khi Docker Desktop Linux engine đã hoạt động:

```powershell
docker info
.\mvnw.cmd -Pintegration test-compile failsafe:integration-test failsafe:verify
```

Chuỗi goal này không chạy lại unit test. `-Pintegration verify` chạy cả unit và integration, nhưng Maven sẽ dừng trước integration nếu unit đang đỏ. Testcontainers không tự skip khi Docker thiếu; lớp integration báo lỗi khởi tạo rõ ràng. Lần đầu cần tải image `postgres:16-alpine`.

DB test tên `jira_regression`, tài khoản và cổng do container cấp. `@DynamicPropertySource` ghi đè datasource ứng dụng. Trước khi truncate dữ liệu, test kiểm tên DB. Không đổi cấu hình test sang DB `jira` của ứng dụng.

Máy Windows này cũng có thể dùng PostgreSQL 18 đã cài khi Docker engine không hoạt động:

```powershell
.\scripts\test-postgres.ps1
```

Script tạo cluster mới `../test-postgres-{GUID}/data`, bind loopback cổng 55432; cổng bận thì dừng. Trước Hibernate DDL, test xác minh đúng URL, user, tên DB và data_directory của cluster vừa tạo. Script dừng đúng cluster đó sau Maven, giữ log để kiểm tra. Không tái sử dụng DB triển khai. Có thể truyền `-JavaHome` để chọn JDK 17 khác; đường dẫn PostgreSQL hiện cấu hình cho máy Windows này trong script.

Migration triển khai: chạy `scripts/migrations/001_issue_uniqueness.sql` bằng `psql -v ON_ERROR_STOP=1 -d <DB-triển-khai> -f <tệp>` sau khi sao lưu. Migration khóa bảng, từ chối dữ liệu trùng và thêm unique nếu chưa có; không tự sửa dữ liệu. Không chạy migration triển khai trên DB người dùng trong phiên kiểm thử này.

Đọc kết quả và coverage:

```powershell
.\mvnw.cmd jacoco:report
```

- Unit/HTTP: `target/surefire-reports/TEST-*.xml` và `*.txt`.
- PostgreSQL: `target/failsafe-reports/TEST-*.xml`, `failsafe-summary.xml`.
- Coverage: `target/site/jacoco/index.html`, `jacoco.csv`, `jacoco.xml`.
- Nếu test đỏ khiến Maven chưa đến bước report, gọi `jacoco:report` riêng. Không sửa assertion chỉ để build xanh.

## Đối chiếu chức năng và nhóm test

| Chức năng đang có | Nhóm test | Nội dung chính |
|---|---|---|
| Đăng ký, đăng nhập, thông tin hiện tại | AuthRegressionTest, ApiRegressionTest | Hash mật khẩu, email trùng, sai mật khẩu, cấp token, HTTP public và wrapper `/me` |
| Refresh/logout | AuthRegressionTest, PostgresRegressionIT | Hết hạn, reuse, thu hồi family, rotation và hai request đồng thời |
| JWT và danh tính | SecurityRegressionTest, ApiRegressionTest | Subject, chữ ký, hết hạn, filter và route cần đăng nhập |
| Quyền toàn cục/dự án | SecurityRegressionTest, ConfigurationRegressionTest | Lead, member, outsider, role có/không có permission, grant trái quyền |
| Người dùng | ProjectRegressionTest | Danh sách, user không tồn tại, quyền xóa |
| Project | ProjectRegressionTest, ApiRegressionTest | Tạo/sửa/xóa, lead theo context, key trùng, audit, quyền đọc |
| Thành viên | ProjectRegressionTest | Thêm/gỡ, khóa ghép, trùng thành viên, giới hạn PM, notification |
| Issue | IssueRegressionTest, PostgresRegressionIT | Quyền, mapper null, cập nhật từng trường, giao việc, mã issue |
| Trạng thái issue | IssueRegressionTest, PostgresRegressionIT | Workflow, lịch sử, không gửi notification trùng, lỗi giữa các bước |
| Bình luận | CollaborationRegressionTest | Quyền, issue không tồn tại, người tạo, thứ tự đọc, người nhận notification |
| Attachment | CollaborationRegressionTest, InfrastructureRegressionTest | Quyền upload/xóa, owner, SDK lỗi và dữ liệu URL |
| Issue type | ConfigurationRegressionTest | Tạo/sửa/xóa, đang được project hoặc issue dùng |
| Issue type theo project | ConfigurationRegressionTest | Gán/gỡ, không gỡ mặc định, đổi mặc định |
| Status và workflow | ConfigurationRegressionTest | Trùng, thiếu dữ liệu, hướng nguồn/đích, rule đang áp dụng |
| Role/permission | ConfigurationRegressionTest | Trùng, đang được sử dụng, gán/gỡ permission và quyền thao tác |
| Board/cột/mapping | ScrumRegressionTest, PostgresRegressionIT | Cột mặc định, map/unmap, reorder và rollback khi có cột khác board |
| Sprint và thêm/gỡ backlog | ScrumRegressionTest, PostgresRegressionIT | FUTURE/ACTIVE/CLOSED, chuyển issue, DONE category, chéo project |
| Notification | CollaborationRegressionTest, ApiRegressionTest | Người nhận, unread, mark read và chặn thông báo của người khác |
| Audit và seeder | CollaborationRegressionTest, InfrastructureRegressionTest, PostgresRegressionIT | Actor/đối tượng, tác vụ hệ thống, migration permission, chạy seed nhiều lần |
| Ràng buộc PostgreSQL | PostgresRegressionIT | Unique email, truy vấn theo project, khóa ghép, rollback, optimistic lock |

HTTP test kiểm mapping và response hiện có. Các kiểm tra bảo mật mong muốn có thể đỏ trên service hiện tại. Việc mock service trong HTTP test không có nghĩa một tài khoản thường được phép thực hiện thao tác quản trị ngoài thực tế.

## Các bug đã biết được gắn nhãn

| Mã | Test | Mong đợi |
|---|---|---|
| BUG-001 | optionalRelationsMustNotCrashMapper | Assignee/parent null không gây NPE |
| BUG-002 | createOrdinaryIssueReturnsGeneratedKey | Issue thường tạo xong phải trả response hợp lệ |
| BUG-003 | rejectCrossProjectIssue | Issue và sprint phải cùng project |
| BUG-004 | closedSprintCannotRestart | Sprint CLOSED không quay lại ACTIVE bằng start |
| BUG-005 | doneCategoryMustRemainInCompletedSprint | Nhóm DONE quyết định hoàn thành, không phụ thuộc tên hiển thị |
| BUG-006 | closedSprintRejectsNewIssue | Sprint đã đóng không nhận thêm issue |
| BUG-007 | outsiderCannotReadProject | Người ngoài không đọc chi tiết project |
| BUG-008 | outsiderCannotListMembers | Người ngoài không đọc danh sách thành viên |
| BUG-009 | ordinaryUserCannotGrantPermissions | User thường không được thay quyền hệ thống |
| BUG-010 | malformed/expired/wrongSignature HTTP tests | JWT lỗi trả 401 có kiểm soát |
| BUG-011 | concurrentRefreshMustNotForkActiveTokens | Một token cũ không sinh hai phiên con còn hiệu lực |
| BUG-012 | concurrentIssueCreationMustReturnDistinctKeys | Hai request tạo issue trả kết quả hợp lệ, không trùng mã |
| BUG-013 | failedNotificationMustNotLeavePartialStatusChange | Lỗi giữa bước không để status và lịch sử commit từng phần |

BUG-011/012 bắt đầu hai request cùng lúc qua service thật và kiểm kết quả đã commit. Test chèn khoảng dừng có giới hạn sau truy vấn repository thật để hai lượt cùng đọc trạng thái cũ trong code hiện tại. Nếu bản sửa dùng khóa để tuần tự hóa, lượt đầu được tiếp tục sau thời gian chờ để không báo deadlock giả. Không viết lại thuật toán tạo issue trong test. Một lần chạy không chứng minh đã xét hết mọi interleaving. Test refresh chấp nhận một lượt thành công và một lượt bị từ chối vì reuse. Lỗi ngoài dự kiến vẫn làm test đỏ. Test issue có thể gặp lỗi mapper trước khi kiểm tra hết tính duy nhất của mã; báo cáo phải phân biệt nguyên nhân thực tế.

BUG-013 kiểm kỳ vọng nguyên tử đã nêu trong plan. Chưa bổ sung outbox/retry hay sửa cách xử lý notification của ứng dụng trong lượt viết test.

Case xóa attachment khi Cloudinary lỗi ghi nhận chính sách hiện tại: vẫn xóa metadata DB. Test xanh này không chứng minh tệp từ xa đã được xóa hoặc có cơ chế retry.

## Khi phát triển tiếp

- Sửa bug nào thì chạy case tái hiện đó trước, sửa trong một plan riêng và chạy lại toàn bộ test liên quan.
- Khi bug đã sửa, bỏ nhãn `known-bug` khỏi case đã xác nhận xanh. Giữ lại test làm hàng rào hồi quy.
- Chức năng mới cần thêm test cùng thay đổi. Không thêm test giả hoặc gọi route chưa tồn tại chỉ để tăng số lượng.
- Chưa đặt ngưỡng coverage tùy ý. Đọc riêng coverage service/security/controller; getter/setter DTO không thay thế kiểm tra nghiệp vụ.
- Không thay exception mong đợi thành `assertThrows(Exception.class)` chỉ để test qua. Đọc lỗi thực tế và xác nhận nhánh được kiểm tra.

## Những phần chưa được bảo đảm

Không có FE/E2E trình duyệt, benchmark tải, dịch vụ Cloudinary thật hoặc mọi tổ hợp quyền/dữ liệu. Các nhánh cấu hình và dữ liệu biên còn có thể bổ sung. Chỉ coi integration đã kiểm chứng sau khi Docker chạy và toàn bộ case PostgreSQL thực sự thực thi; biên dịch thành công không thay thế bước đó.
