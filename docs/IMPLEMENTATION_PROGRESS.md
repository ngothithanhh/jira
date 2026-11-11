# Tiến độ hoàn thiện backend

## Quyền triển khai

Người dùng đã duyệt plan sửa bug và cho phép tiếp tục tự động các phần còn thiếu. Theo chỉ dẫn mới nhất, agent chính tự lập và kiểm tra plan, code, kiểm thử và commit; không dùng sub-agent. Mỗi phần có test và commit riêng. Không push remote khi chưa được yêu cầu.

## Điểm bắt đầu

- Commit gốc: `a26542c`.
- Regression trước sửa: 235 case, 222 đạt, 13 đỏ khớp bug đã biết.
- 10 PostgreSQL integration case đã viết; Docker ban đầu không khởi động.
- Thay đổi cục bộ có sẵn trước tác vụ được giữ nguyên. Cache Maven và secret không được commit.

## Các đợt

| Đợt | Phạm vi | Trạng thái | Commit |
|---|---|---|---|
| 1 | JWT, quyền truy cập, nullable issue DTO, transaction issue, số issue đồng thời, vòng đời sprint, refresh rotation | 243 unit/HTTP + 11 PostgreSQL đạt; đã tự kiểm tra | ea1e39a |
| 2 | Sprint backlog và product backlog, thứ tự issue | 263 unit/HTTP + 16 PostgreSQL đạt; đã tự kiểm tra | 3a1a092 |
| 3 | Issue trên board, di chuyển, vị trí và WIP | 281 unit/HTTP + 20 PostgreSQL đạt; đã tự kiểm tra | Xem git log |
| 4 | Chi tiết/xóa issue, tìm kiếm, lọc, phân trang | Chờ plan chi tiết | Chưa commit |
| 5 | Sửa/xóa sprint, board, column | Chờ plan chi tiết | Chưa commit |
| 6 | Tổng kết sprint, velocity, burndown | Chờ plan chi tiết | Chưa commit |
| 7 | Quản lý người dùng/tài khoản, vai trò thành viên | Chờ plan chi tiết | Chưa commit |
| 8 | Sửa/xóa comment, notification, tra cứu audit | Chờ plan chi tiết | Chưa commit |

## Điều kiện ghi nhận hoàn tất

Chỉ ghi test đã thực thi thực tế. Case không chạy do môi trường phải ghi rõ, không được chuyển thành skip để tạo báo cáo xanh. Giữ API tương thích khi có thể; các trường nullable phải trả null đúng nghĩa. Thay đổi schema cần kiểm tra dữ liệu hiện có, không tự xóa hoặc đổi dữ liệu cũ.

Mỗi đợt chỉ commit sau khi agent chính tự kiểm tra và chạy test liên quan. Các bug mới được ghi nhận và xử lý theo quyền tự triển khai đã được giao. Không coi hoàn thiện backend là đã hoàn thiện frontend, vì chưa có mã nguồn FE trong checkout.

Đợt 2: API/giới hạn tại BACKLOG_API.md, plan tại BACKLOG_PLAN.md. Đã tự kiểm tra caller/controller, quyền trước query, thứ tự khóa/refresh và nhánh rollback. Hai test DB xác nhận membership và status phải chờ reorder; test lỗi sau flush xác nhận không commit rank dở. Migration 002 chạy hai lần trên cluster PostgreSQL 18.6 riêng. Log `outputs/regression/backlog-unit-final.log` và `backlog-postgres.log`. Không kiểm frontend, tải lớn hay mọi interleaving. Hai reorder cùng scope vẫn theo thứ tự commit, chưa có revision toàn backlog.
