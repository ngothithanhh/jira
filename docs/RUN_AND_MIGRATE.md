# Chạy backend và cập nhật schema

Dùng JDK 17, Maven wrapper đã có trong repo và PostgreSQL. Docker chỉ cần nếu chọn integration Testcontainers; script Windows có thể tạo cluster test bằng PostgreSQL 18 đã cài.

Cấu hình cá nhân hiện có được giữ nguyên và không commit. Nếu checkout mới chưa có file cấu hình, copy docs/application-example.properties sang src/main/resources/application.properties. File mẫu dùng biến môi trường DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET; JWT_SECRET cần đủ dài cho HS256 (ít nhất 32 byte). Cấu hình Cloudinary khi cần upload/xóa attachment; các API Scrum/inbox không cần gọi dịch vụ này. Không đưa mật khẩu/khóa vào Git.

## DB đã có dữ liệu

1. Dừng phiên bản backend cũ, sao lưu DB và xác nhận đúng tên DB/host đang cập nhật.
2. Chạy lần lượt các script 001..006 trong scripts/migrations bằng psql -v ON_ERROR_STOP=1, chỉ định -h/-p/-U/-d rõ ràng. psql có thể hỏi mật khẩu tương tác; không ghi mật khẩu vào lệnh hoặc log.
3. Migration 001 từ chối dữ liệu trùng project/issue_number hoặc issue_key; giải quyết dữ liệu trước khi chạy lại, script không tự xóa. 003 giữ counter số issue đã cấp; 004 không dựng lại lịch sử Scrum cũ.
4. Chạy bản mới với spring.jpa.hibernate.ddl-auto=validate (DB_DDL_MODE=validate nếu dùng file mẫu). Hibernate validate không thay thế migration. Nếu schema cũ không khớp các entity gốc khác, cần xử lý sai khác riêng; không tự xóa bảng.

Ví dụ một script (thay tham số bằng DB triển khai đúng):

    psql -h localhost -p 5432 -U postgres -d jira -v ON_ERROR_STOP=1 -f scripts/migrations/001_issue_uniqueness.sql

Chưa chạy các script này trên DB triển khai trong đợt hoàn thiện code; các lần kiểm chứng dùng DB test riêng.

## DB mới hoàn toàn

Repo gốc chưa có baseline SQL đầy đủ cho các bảng cũ. Trên DB mới/rỗng đã xác nhận đúng đích, có thể khởi tạo lần đầu bằng Hibernate update (DB_DDL_MODE=update với file mẫu), sau đó dừng app, chạy 001..006 để bổ sung index/default và chuyển về validate. Không dùng create/create-drop trên DB có dữ liệu.

Seeder hiện có tạo role/permission và tài khoản khởi tạo theo logic repo. Sau khi bootstrap, dùng chức năng đổi mật khẩu/quản lý admin; cấu hình seeder vẫn theo mã gốc. Tài liệu này không đưa credential khởi tạo vào log hoặc file mẫu.

## Khởi chạy và kiểm tra

    $env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-17.0.20.1'
    .\mvnw.cmd spring-boot:run

Swagger UI: /swagger-ui/index.html; OpenAPI JSON: /v3/api-docs. Bấm Authorize và nhập access token theo bearerAuth, các API nghiệp vụ gửi Authorization: Bearer <token>.

Unit/MockMvc: .\mvnw.cmd clean test.

Toàn bộ kiểm tra trên máy Windows này: .\scripts\test-postgres.ps1 -WithUnit. Script tạo cluster mới loopback:55432, kiểm URL/user/database/data_directory trước DDL, chạy clean verify rồi dừng đúng cluster của test. Cổng đang bận sẽ dừng, không tiếp quản PostgreSQL khác. Log/data test được giữ trong ../test-postgres-{GUID}. Xem REGRESSION_TESTS.md cho cách dùng Docker thay thế.

Script và đường dẫn JDK/PostgreSQL hiện theo máy Windows này; có thể truyền -JavaHome hoặc chỉnh pgBin khi chuyển máy. Không copy jar trong target sang nơi khác khi nó đang chứa application.properties cá nhân do Maven resources copy.
