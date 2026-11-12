-- Nội dung nhị phân tách khỏi metadata để GET danh sách không đọc toàn bộ file.
BEGIN;
ALTER TABLE issue_attachments ADD COLUMN IF NOT EXISTS storage_type varchar(16);
CREATE TABLE IF NOT EXISTS issue_attachment_contents (
    attachment_id integer PRIMARY KEY REFERENCES issue_attachments(attachment_id) ON DELETE CASCADE,
    content bytea NOT NULL
);
COMMIT;
