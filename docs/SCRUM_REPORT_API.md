# Báo cáo Scrum

Bearer JWT và VIEW_PROJECT trên project sở hữu sprint/tài nguyên bắt buộc.

| GET route | Nội dung |
|---|---|
| `/api/sprints/{id}/reports/summary` | Tổng/remaining/DONE hiện tại và commitment/completed snapshot nếu có |
| `/api/sprints/{id}/reports/burndown` | Planned dates, observed start, finishedAt, baselineAvailable và event points |
| `/api/projects/{id}/reports/velocity?limit=10` | CLOSED mới nhất theo snapshot kết thúc, tối đa 50; legacy fallback ID |

Summary `current` gồm totalPoints, remainingPoints, issueCount, remainingCount, invalidEstimateCount. `completedPoints`/`completedCount` ở cấp summary là snapshot lúc đóng, không phải dữ liệu tính lại. `committedPoints`/`committedCount` là lúc bắt đầu hoặc lần đầu quan sát tùy baselineAvailable.

Burndown là chuỗi event `{recordedAt,totalPoints,remainingPoints,issueCount,remainingCount,invalidEstimateCount}` theo thời gian/ID, để FE vẽ step chart. Biến đổi total thể hiện scope/estimate changes. Không tự điền 0 trước khi có dữ liệu, chưa cung cấp chuỗi bucket theo ngày hoặc đường lý tưởng. Timestamp theo quy ước LocalDateTime hiện có của backend.

Snapshot START_EVENT được tạo cùng transaction bắt đầu sprint. Status, estimate, thêm/gỡ/xóa issue trong ACTIVE cập nhật event nếu số liệu thay đổi. Đóng sprint chốt completed trước khi chuyển việc chưa DONE, rồi ghi điểm cuối. Chỉnh/xóa issue CLOSED về sau không thay completed snapshot. Các trường tổng dùng long; estimate âm mới bị từ chối 400, estimate âm legacy tính 0 và được đếm trong invalidEstimateCount.

Sprint ACTIVE cũ chưa snapshot có origin OBSERVED từ lần mutation đầu tiên, `baselineAvailable=false`. CLOSED cũ chưa snapshot trả số lịch sử null/`completionAvailable=false`; dữ liệu hiện tại vẫn xem được ở summary. Không có bằng chứng để backfill lịch sử trước khi tính năng được cài. Ghi DB trực tiếp ngoài các API được tích hợp không tạo event.

Migration triển khai: `scripts/migrations/004_scrum_metrics.sql` sau backup, bằng `psql -v ON_ERROR_STOP=1`; chạy lặp an toàn, không backfill số liệu đoán. Phiên này chỉ chạy trên DB test.

Kiểm chứng: 334 unit/MockMvc + 33 PostgreSQL 18.6 đều đạt, 0 skip. DB kiểm chuỗi start/estimate/status/remove/add/complete, velocity bất biến sau sửa/xóa issue, legacy missing history và rollback cả start/complete khi ghi point lỗi. Migration 004 chạy hai lần. Log `outputs/regression/reports-unit.log`, `reports-postgres.log`. Agent chính tự kiểm tra caller, khóa project, thứ tự chụp trước/sau chuyển scope và transaction, không dùng sub-agent.

Giới hạn: chưa có FE/chart E2E, benchmark hoặc snapshot mọi thao tác SQL ngoài backend. Mỗi thay đổi ACTIVE đọc lại backlog để tính số liệu; có chi phí theo số issue trong sprint, chưa tối ưu bằng tổng tích lũy. Chưa có chính sách lưu trữ/xóa event theo thời hạn.
