# Comment và attachment lưu trong PostgreSQL local

Attachment mới lưu hoàn toàn trong DB ứng dụng. Không cần tài khoản/credential Cloudinary. Hai bean Cloudinary cũ chỉ được tạo khi chủ động bật app.storage.cloudinary-enabled=true; luồng attachment hiện tại vẫn chỉ dùng local DB.

| API | Quyền và hành vi |
|---|---|
| GET /api/issues/{issueId}/comments | VIEW_PROJECT, đọc đúng issue |
| POST cùng route | VIEW_PROJECT, body commentText 1..10000 ký tự; author từ JWT; comment/audit/notification cùng transaction |
| PUT /api/issues/{issueId}/comments/{commentId} | Tác giả còn VIEW_PROJECT hoặc EDIT_ISSUE; body commentText/expectedVersion |
| DELETE cùng route | expectedVersion query; version cũ409, sai parent404 |
| GET /api/issues/{issueId}/attachments | VIEW_PROJECT, chỉ metadata, không đọc bytea |
| POST cùng route | EDIT_ISSUE, đúng một multipart part file, không rỗng và tối đa10MiB; trả201 |
| GET /api/issues/{issueId}/attachments/{attachmentId}/content | VIEW_PROJECT và parent khớp; trả byte thật, attachment/nosniff/no-store |
| DELETE /api/issues/{issueId}/attachments/{attachmentId} | VIEW_PROJECT và chủ upload hoặc EDIT_ISSUE; xóa content + metadata cùng transaction, trả204 |

Route DELETE /api/issues/attachments/{attachmentId} cũ vẫn hoạt động với cùng kiểm quyền và transaction. FE mới chỉ mở route có parent issue.

Metadata trong issue_attachments: file_name, content_type, file_size, storage_type=LOCAL_DB; người tải và issue lấy từ JWT/DB. Bytea nằm riêng trong issue_attachment_contents với attachment_id PK/FK. Không có quan hệ eager từ metadata sang content. Upload lỗi rollback cả hai bảng; delete dùng bulk query content rồi xóa metadata, không tải file lên RAM chỉ để xóa. Khóa project và refresh membership actor trước kiểm quyền lần hai; hai lượt xóa cùng file chỉ một lượt thành công.

Content-Type do người upload khai chỉ là metadata. API content nhận dạng raster theo magic bytes; mọi loại khác, gồm HTML/SVG, trả application/octet-stream. Content-Disposition luôn attachment với filename UTF-8; X-Content-Type-Options=nosniff và Cache-Control=no-store, private. Client chỉ preview ảnh raster từ Blob đã tải qua API có xác thực, không iframe nội dung người dùng.

Response giữ các field cũ, thêm fileName/contentType/fileSize nullable và downloadAvailable. Tệp legacy chỉ có URL không tự được tải/chuyển từ dịch vụ ngoài; downloadAvailable=false, GET content409. Xóa legacy chỉ gỡ dữ liệu local, không khẳng định đã xóa asset ngoài DB.

## Migration và chạy

Sau backup/chọn đúng DB, áp dụng 007_attachment_metadata.sql và 008_attachment_local_content.sql sau các migration trước. 007 đã chạy ở máy chính trước khi người dùng đổi yêu cầu sang local DB nên giữ checksum, các cột cloud nullable để tương thích; 008 không điền bytea giả cho URL cũ. scripts/migrate.ps1 tự áp dụng theo thứ tự và ledger.

AttachmentConfig nạp application-upload.properties: file10MiB, request11MiB để có overhead multipart. BFF giới hạn file10MiB và request với overhead nhỏ, kiểm byte thực chứ không tin Content-Length. Chạy bằng JDK17 và spring.jpa.hibernate.ddl-auto=validate. DB demo đã được backup và chạy migration007/008 lặp thành công; không sửa issue/board của SHOP.

## Kiểm chứng

Unit/API kiểm quyền trước ghi, quyền đổi trong khi chờ, owner đã mất VIEW, owner null, parent sai, multipart, kích thước, legacy không fetch URL, MIME giả ảnh và list không gọi repository nội dung. PostgreSQL integration kiểm metadata/bytea tải lại đúng byte, migration lặp, rollback upload/delete cả hai bảng và hai lượt delete đồng thời. Các test PostgreSQL này mock ProjectSecurity; browser dùng Spring Security và quyền DB thật.

Không coi test đọc code/build hoặc mock là bằng chứng HTTP/DB thật. Kết quả chạy cuối và luồng kiểm bằng tay nằm trong docs/COLLABORATION_CONNECTED.md của FE. Giới hạn10MiB nhằm giữ upload/download bytea trong RAM có giới hạn; chưa benchmark file lớn/nhiều người tải hoặc mọi interleaving.
