# Comment, inbox notification và audit quản trị

Tất cả route dùng Bearer JWT.

| Route | Quyền và hành vi |
|---|---|
| PUT /api/issues/{issueId}/comments/{commentId} | VIEW_PROJECT và tác giả hoặc EDIT_ISSUE; body commentText/expectedVersion, trả CommentResponse mới |
| DELETE cùng route | expectedVersion query, cùng quyền, 204 |
| GET /api/notifications/unread-count | Chỉ inbox của người đang đăng nhập; trả {count} |
| PUT /api/notifications/read-all | Chỉ user hiện tại/read=false; trả {updated}, gọi lại trả 0 |
| DELETE /api/notifications/{id} | Chỉ người nhận; 204, khác người 403 |
| GET /api/admin/audit-logs | SYSTEM_ADMIN; phân trang/filter tại DB |
| GET /api/admin/overview | SYSTEM_ADMIN; các count user/enabled user/admin/project/issue/ACTIVE/CLOSED |

Comment text có 1..10000 ký tự, bản ghi chứa version và updatedAt. PUT nhận {"commentText":"Nội dung mới","expectedVersion":0}. DELETE thiếu version hoặc body sai trả 400, version cũ 409. Comment không thuộc issue trên URL trả 404. Tác giả đã rời project vẫn cần VIEW_PROJECT. Sửa/xóa không gửi lại notification “bình luận mới”. Audit/comment/notification tạo mới cùng transaction; lỗi rollback. Hai sửa cùng version chỉ một được commit. Mapper giữ userId null cho dữ liệu legacy không có tác giả.

Audit query hỗ trợ entityType/entityId, actorId, action, createdFrom/createdTo ISO LocalDateTime, page (0-based), size (1..100, mặc định 20). entityId phải kèm entityType; ngày đảo, ID không dương hoặc offset quá lớn trả 400. Sort createdAt DESC/auditId DESC, response {items,page,size,totalItems,totalPages}; actor có thể null cho thao tác hệ thống. Không trả entity/credential.

Migration triển khai: scripts/migrations/006_collaboration_indexes.sql sau backup bằng psql -v ON_ERROR_STOP=1. Chuyển comment_text sang text, backfill version 0, thêm updated_at và index audit/inbox; chạy lặp được. Phiên này chỉ áp dụng lên PostgreSQL test riêng.

Kiểm chứng: 371 unit/MockMvc + 49 PostgreSQL 18.6 đều đạt, 0 failure/error/skip. DB kiểm hai edit cùng version, stale delete, audit rollback, comment tạo đồng thời với issue delete, inbox owner/read-all idempotent, audit filter/count/null actor, overview và migration lặp/text dài. Log outputs/regression/collaboration-admin-unit.log và collaboration-admin-postgres.log.

Agent chính tự kiểm tra quyền/tác giả, project-lock/refresh/version, scope inbox, predicate audit và rollback; không dùng sub-agent. Chưa có FE/E2E, benchmark, realtime hoặc dịch vụ ngoài. Inbox hiện hỗ trợ polling; read-all không bao gồm notification được tạo sau câu UPDATE. Audit trả dữ liệu đã có, chưa khẳng định mọi thao tác cũ đều đã ghi audit.
