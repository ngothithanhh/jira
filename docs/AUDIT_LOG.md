# Nghiệm thu audit log

- Toàn hệ thống: GET /api/admin/audit-logs, chỉ SYSTEM_ADMIN, có thể lọc projectId.
- Một project: GET /api/projects/{projectId}/audit-logs, cần UPDATE_PROJECT trên đúng project (lead và SYSTEM_ADMIN có quyền theo chính sách hiện hành). Query không nhận projectId. Hai tuyến chỉ đọc, sắp xếp createdAt rồi auditId giảm dần. Count và danh sách dùng cùng phạm vi.
- entityId phải đi cùng entityType khi lọc. Chọn actorId hoặc systemOnly=true. Lọc action theo mã đầy đủ, bỏ khoảng trắng đầu/cuối và không phân biệt hoa thường. Ngày giờ theo LocalDateTime của DB, không kèm offset. page bắt đầu từ 0, size từ 1 đến 100.
- projectId là snapshot khi ghi, không có FK. Log còn đọc được sau khi entity bị xóa. Log toàn cục và log cũ không suy ra project chỉ hiện ở toàn hệ thống. Nhật ký không tự tạo thêm lịch sử còn thiếu; không lưu token, mật khẩu, nội dung bình luận hoặc dữ liệu tệp.

## Migration 014

Dùng pg_dump định dạng custom trước khi chạy. Điền biến cho đúng DB audit đã đối chiếu và cung cấp mật khẩu qua pgpass hoặc PGPASSWORD. Dừng nếu lệnh backup hoặc kiểm danh sách backup thất bại.

    & "$pgBin\pg_dump.exe" -h 127.0.0.1 -p $port -U $dbUser -d $dbName -Fc -f $backup
    & "$pgBin\pg_restore.exe" -l $backup
    .\scripts\migrate.ps1 -Database $dbName -Port $port -UserName $dbUser
    .\scripts\migrate.ps1 -Database $dbName -Port $port -UserName $dbUser

Đối chiếu schema_migrations có đúng một dòng 014_audit_project_scope.sql cùng checksum và pg_indexes có ix_audit_project_order. Kiểm một log ISSUE cũ còn đối tượng: project_id phải khớp project của issue. Một log cũ không tìm được đối tượng phải giữ NULL. Khi khởi động BE với schema đã migrate, dùng ddl-auto=validate.

## Hồi quy

    $env:APP_JWT_EXPIRATION = '600000'
    pwsh -NoProfile -File .\scripts\test-postgres.ps1 -JavaHome 'C:\Program Files\Java\jdk-22' -WithUnit

Runner dùng cluster PostgreSQL riêng. PostgresRegressionIT chạy SQL 014 trên schema test, kiểm snapshot còn sau xóa, thứ tự khi trùng timestamp, count/page, system actor và rollback. SystemSmokeIT kiểm GET bằng JWT thật với admin, lead, member có UPDATE_PROJECT, member chỉ VIEW_PROJECT và sau thu hồi quyền. Khi kiểm bằng tay, thử query projectId khác trên URL project (400), đọc project không có quyền (403) và lọc ISSUE đã xóa theo mã DELETE.
