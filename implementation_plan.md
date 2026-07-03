# Kế hoạch Lập trình Backend (Spring Boot) - Chi tiết Mở rộng

Dựa trên yêu cầu của bạn, hệ thống không chỉ cần các tính năng để User sử dụng, mà còn cần một bộ module cho **Quản trị viên (Admin/Super Admin)** để có thể linh hoạt cấu hình hệ thống mà không cần sửa code. 

Dưới đây là kế hoạch phát triển chi tiết mọi khía cạnh để đạt được sự "linh động" giống như Jira thật.

## User Review Required
> [!IMPORTANT]
> Đây là một bản quy hoạch ở mức độ "Enterprise". Việc cho phép Admin tự tạo `Issue Type`, `Role`, `Permission` sẽ yêu cầu Frontend phải xây dựng màn hình System Settings (Cài đặt hệ thống). Bạn có muốn làm phần Admin Settings này trước, hay hoàn thiện phần Workspace của User trước?

## Kế hoạch Triển khai (Proposed Changes)

---

### Giai đoạn 1: Quản trị Hệ thống (System Settings & Configurations)
Đây là phần nền tảng giúp hệ thống "sống" và có thể tuỳ biến cho mọi tổ chức.

1. **[NEW] `IssueTypeController` & Service:**
   - Cung cấp API (CRUD) để Admin tự tạo loại công việc: `Epic`, `Story`, `Task`, `Bug`, `Sub-task`...
   - Cấu hình Icon/Màu sắc cho từng loại Task.
2. **[NEW] `IssueStatusController` & Service:**
   - Cung cấp API (CRUD) để định nghĩa các trạng thái hệ thống: `To Do`, `In Progress`, `Review`, `Testing`, `Done`...
3. **[NEW] `PriorityController` & Service:**
   - Quản lý các cấp độ ưu tiên: `Highest`, `High`, `Medium`, `Low`, `Lowest` kèm mã màu.

---

### Giai đoạn 2: Phân quyền Động (Dynamic Roles & Permissions)
Hiện tại `UserRoleController` mới chỉ có API lấy danh sách (`GET`). Cần bổ sung CRUD và gán quyền động.

1. **Cập nhật `UserRoleController`:**
   - Thêm API tạo mới, sửa tên, xoá Vai trò (Roles: `Admin`, `Project Manager`, `Developer`, `QA`...).
2. **[NEW] `PermissionController`:**
   - Lấy danh sách toàn bộ các quyền hệ thống đang có (dựa trên Enum `PermissionName` hoặc bảng Permissions).
3. **[NEW] `RolePermissionController`:**
   - API `POST /api/roles/{roleId}/permissions`: Cấp một bộ quyền (CREATE_ISSUE, DELETE_PROJECT...) cho một Role.
   - API `DELETE /api/roles/{roleId}/permissions`: Thu hồi quyền.
   - *Tác dụng:* Admin trên giao diện có thể tick/untick để cho phép Developer được xoá Task hay không mà không cần nhờ Dev backend sửa code.

---

### Giai đoạn 3: Cấu hình Quy trình (Workflow & Transitions)
Jira nổi tiếng ở việc không phải trạng thái nào cũng nhảy tuỳ tiện được.
1. **[NEW] `WorkflowController`:**
   - Định nghĩa **Quy tắc chuyển trạng thái (Transitions Rules)**.
   - Ví dụ: Admin thiết lập Rule chỉ cho phép kéo Task từ `To Do` sang `In Progress`, không cho kéo thẳng từ `To Do` sang `Done`.
   - Cập nhật lại `changeIssueStatus` trong `IssueService` để kiểm tra: "Status mới này có được phép chuyển từ Status hiện tại không?".

---

### Giai đoạn 4: Quản lý Bảng (Board & Columns)
1. **Cập nhật `BoardController`:**
   - Thêm API cấu hình cột trên Kanban Board.
   - API `Map Status -> Column`: Ví dụ Admin gom 2 status `In Review` và `Testing` vào chung cột **"Đang Kiểm Tra"** trên giao diện Board.
   - Thay đổi thứ tự (Order) hiển thị của cột.

---

### Giai đoạn 5: Quản lý Thời gian (Time Tracking & Worklog)
1. **[NEW] `WorkLogController`:**
   - Log time: User nhập số giờ đã làm cho một task (ví dụ "2h 30m").
   - Lấy lịch sử log time của task để tính tổng thời gian (Time Spent) so với Ước tính ban đầu (Original Estimate).
2. **Cập nhật `IssueService`:**
   - Tự động trừ `Remaining Estimate` (Thời gian ước tính còn lại).

---

### Giai đoạn 6: Theo dõi Lịch sử (Audit Log / Activity Stream)
"Ai đã làm gì vào lúc nào?"
1. **[NEW] `AuditLogController`:**
   - API lấy toàn bộ lịch sử thay đổi của dự án hoặc của một Task cụ thể (Ví dụ: "Hải đã đổi mức ưu tiên từ Low sang High lúc 10:00").
2. **Event Listeners:** Sử dụng Spring AOP hoặc Hibernate Envers để tự động bắt sự kiện và lưu vào bảng `AuditLog` mà không cần viết code thủ công ở từng hàm service.

---

### Giai đoạn 7: Thống kê & Báo cáo (Reporting & Dashboard)
1. **[NEW] `ReportController`:**
   - Cung cấp dữ liệu toạ độ cho **Burndown Chart** trong Sprint.
   - Báo cáo **Khối lượng công việc (Workload)** của từng Dev trong dự án.
   - Báo cáo **Velocity** (Tốc độ hoàn thành điểm story) qua các Sprint.

---

### Giai đoạn 8: Tìm kiếm Nâng cao (Advanced Search & JQL)
1. **Cập nhật `IssueController`:**
   - Xây dựng API `/api/issues/search` với các bộ lọc động (Dynamic Query bằng JPA Specifications).
   - Lọc theo: Người làm, Trạng thái, Loại task, Người tạo, Ngày hết hạn, text search.

## Verification Plan
Do khối lượng module rất lớn, phương pháp kiểm tra (Verify) sẽ đi theo từng Giai đoạn:
1. **Hoàn thiện Phase 1 & 2:** Đảm bảo Super Admin dùng API để cấu hình được Role, gán Permission, và tạo được hệ thống Issue Types.
2. **Hoàn thiện Phase 3 & 4:** Đảm bảo luồng tạo Task, phân quyền chuyển trạng thái Task hoạt động đúng với cấu hình Workflow mới tạo.
3. **Hoàn thiện các Phase sau:** Bổ trợ nghiệp vụ log time, vẽ biểu đồ và tìm kiếm nhanh.
