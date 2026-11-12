-- Chạy sau 000..006; cột nullable giữ tương thích tệp cũ, không tự dựng tên/dung lượng giả.
BEGIN;
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS file_name varchar(255);
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS content_type varchar(255);
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS file_size bigint;
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS cloud_public_id varchar(1024);
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS cloud_resource_type varchar(10);
COMMIT;
