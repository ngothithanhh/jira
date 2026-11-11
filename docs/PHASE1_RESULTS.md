# Đợt 1 — sửa lỗi và hồi quy

Ngày 11/11/2026, agent chính tự kiểm tra, không dùng sub-agent theo chỉ dẫn mới nhất.

## Kết quả thực thi

| Lệnh | Kết quả |
|---|---|
| JDK 17, `mvnw.cmd -B -ntp clean test` | 243 test, 0 failure, 0 error, 0 skip |
| `scripts/test-postgres.ps1` | 11 integration test, 0 failure, 0 error, 0 skip |
| `git diff --check` | Đạt |

Log: `outputs/regression/phase1-unit-final.log`, `phase1-postgres-final.log` ở workspace cấp trên; báo cáo từng case trong target/surefire-reports và target/failsafe-reports. PostgreSQL thực chạy là **18.6** trên cluster mới ở cổng 55432; chưa kiểm lại image PostgreSQL 16 vì Docker engine không hoạt động. Cluster test đã dừng sau Maven.

## Thay đổi và bằng chứng

- Giữ null cho assignee/reporter/parent trong DTO; tạo issue thường không còn lỗi mapper. Tạo issue khóa project từ trước MAX đến commit và có unique project/number, issue_key.
- Mutation issue có transaction: lỗi notification không để status/lịch sử lưu dở. PostgreSQL kiểm rollback và optimistic locking thực.
- JWT sai cấu trúc/hạn/chữ ký trả JSON 401; security stateless. Outsider không đọc chi tiết project, thành viên, board, sprint. Thành viên được VIEW_PROJECT; SYSTEM_ADMIN xác định bằng role toàn cục.
- Chặn sửa cấu hình role, permission, status, type và workflow khi không phải admin; gán type vào project cần quyền quản lý project. Test tách biệt gate service và logic quyền thật.
- Sprint chặn issue khác project, thay đổi CLOSED, mở nhiều ACTIVE; DONE theo category. Khóa project chung chặn thêm vào nhiều sprint mở và tuần tự hóa start/complete.
- Refresh khóa token trước đọc/rotation; reuse thu hồi family và commit trước khi trả lỗi. PostgreSQL kiểm hai lượt cùng refresh không tạo hai token con còn hiệu lực.
- Migration 001 từ chối dữ liệu trùng trước thêm unique, chạy lặp được. Integration chạy migration hai lần và kiểm DB từ chối số issue trùng. Chưa chạy trên DB triển khai.

## Tự kiểm tra

| Tiêu chí | Kết luận và giới hạn |
|---|---|
| Correctness | Đọc caller/service/repository cho các luồng trên; 13 case đỏ baseline đã xanh và giữ lại làm hồi quy. Chưa chứng minh mọi nghiệp vụ hệ thống đầy đủ. |
| Readability | Comment tiếng Việt giải thích ID, quyền, bước ghi và rollback ở luồng sửa. |
| Maintainability | Tái dùng GlobalSecurity/ProjectSecurity và repository, không thêm framework xử lý nghiệp vụ riêng. |
| Performance | Khóa theo project tuần tự hóa ghi trong cùng dự án; có thể nghẽn khi tải cao. Chưa benchmark. Danh sách projects còn lọc quyền sau truy vấn, chưa tối ưu tải lớn. |
| Security | Admin toàn cục tách role project, JWT lỗi không đi tiếp controller. Integration mock quyền nên không chứng minh auth đầu cuối trên server thật. |
| Error handling | 401/403/404/409 có JSON; lỗi unique/version trả 409. Không tự retry các mutation. Các RuntimeException ngoài phạm vi vẫn cần chuẩn hóa ở đợt sau. |
| Testing | Unit/MockMvc, SQL và hai luồng đồng thời đã chạy. Một số liên lịch không đại diện mọi interleaving; chưa load test. |
| Coding standards | Java/Spring theo repo, không có Mushroom/generated cần sửa; không tạo sub-agent. |
| Architecture | Transaction ở service, khóa DB ở repository, lỗi HTTP tập trung; giữ API cũ và chỉ sửa nghĩa ID nullable. |
| Documentation | Có lệnh test, migration và plan backlog. Baseline lưu riêng, không thay kết quả cũ bằng số xanh mới. |

## Chưa hoàn thành

Các đợt chức năng còn thiếu nằm trong IMPLEMENTATION_PROGRESS.md. Chưa có FE trong checkout, chưa kiểm E2E trình duyệt, SMTP/Cloudinary thật, chưa triển khai migration lên DB ứng dụng. Các secret/cấu hình cục bộ và cache Maven không đưa vào commit. Thay đổi AuditLog và docker-compose có sẵn từ trước được giữ ngoài commit đợt này.
