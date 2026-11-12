# Issue type, status, workflow và loại công việc của project

## API

| Method | Endpoint (sau /api) | Dữ liệu | Quyền |
| --- | --- | --- | --- |
| GET | issue-types / issue-statuses / workflows/rules | Danh mục thật | Đã xác thực |
| POST | issue-types | typeName | SYSTEM_ADMIN |
| PUT | issue-types/{id} | typeName | SYSTEM_ADMIN |
| DELETE | issue-types/{id} | Không body | SYSTEM_ADMIN |
| POST | issue-statuses | statusName,statusCategory | SYSTEM_ADMIN |
| PUT | issue-statuses/{id} | statusName,statusCategory hiện tại | SYSTEM_ADMIN |
| DELETE | issue-statuses/{id} | Không body | SYSTEM_ADMIN |
| POST | workflows/rules | fromStatusId,toStatusId | SYSTEM_ADMIN |
| DELETE | workflows/rules/{id} | Không body | SYSTEM_ADMIN |
| GET | workflows/allowed-transitions?currentStatusId=... | Các status đích | Đã xác thực |
| GET | projects/{projectId}/issue-types | Các loại được gán; boolean tên default | VIEW_PROJECT |
| POST | projects/{projectId}/issue-types/{typeId}?isDefault=true | Query isDefault tùy chọn, mặc định false | MANAGE_BOARD hoặc UPDATE_PROJECT |
| PUT | projects/{projectId}/issue-types/{typeId}/default | Không body | Như trên |
| DELETE | projects/{projectId}/issue-types/{typeId} | Không body | Như trên |

Tên trim 1–50 ký tự. ID là khóa DB nguyên dương. Nguồn và đích workflow phải khác nhau. StatusCategory chỉ TO_DO,IN_PROGRESS,DONE; sau khi tạo nhóm cố định, PUT khác nhóm trả409.

400: body không hợp lệ. 401: thiếu/hết phiên. 403: thiếu quyền. 404: bản ghi không tồn tại hoặc loại chưa gán. 409: tên/cạnh trùng, nhóm khác nhóm ban đầu, loại mặc định/đang dùng hoặc cấu hình còn tham chiếu.

Không tự gỡ mapping/rule/lịch sử để xóa status. Không thay loại của issue khi gỡ project type. Thêm/xóa rule không đổi issue hiện tại.

## Transaction và chạy đồng thời

Project type mutations khóa projects theo projectId, refresh project và membership người gọi rồi kiểm lại quyền. Tìm target trước sửa default; flush toàn bộ cờ false trước ghi một true. Gán isDefault=true cũng dùng cùng luồng. Lỗi sau clear/insert rollback cả transaction.

Xóa project type và catalog type dùng bulk delete có điều kiện để tránh cascade persist từ collection đã load tạo lại liên kết. FK bảo vệ tham chiếu phát sinh sau bước kiểm tra. Các mapper quan hệ lazy chạy trong transaction, không phụ thuộc Open EntityManager In View.

## Migration và kiểm thử

009_configuration_integrity.sql bổ sung unique partial index tối đa một is_default=true mỗi project và unique cạnh from_status_id/to_status_id. Dữ liệu legacy trùng làm migration dừng trước thay đổi; không tự sửa/xóa bản ghi. Dùng scripts/migrate.ps1 -Database <db> sau backup; ledger/checksum giữ migration cũ bất biến.

scripts/test-postgres.ps1 -WithUnit tạo cluster mới cổng55432 và xác minh đích trước Hibernate create-drop. Integration kiểm migration chạy lặp, đổi default đồng thời, cạnh trùng đồng thời, rollback sau clear với lỗi insert, nhóm bất biến, tham chiếu và gỡ project type thực sự sau refresh/cascade. API/browser local kiểm quyền thật; ProjectSecurity ở DataJpaTest là mock, không dùng kết quả đó để khẳng định phân quyền HTTP.
