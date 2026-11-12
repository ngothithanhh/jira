-- Chụp phạm vi project mà không ràng buộc FK: xóa entity/project không xóa lịch sử.
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS project_id integer;
CREATE INDEX IF NOT EXISTS ix_audit_project_order ON audit_logs(project_id, created_at DESC, audit_id DESC);

-- PROJECT tự xác định phạm vi kể cả project đã mất; không suy project từ actor hay mô tả.
UPDATE audit_logs SET project_id=entity_id WHERE project_id IS NULL AND entity_type='PROJECT' AND entity_id>0;
-- Chỉ backfill con còn tồn tại và được tạo không muộn hơn log; con đã xóa để NULL, chỉ admin xem.
UPDATE audit_logs a SET project_id=i.project_id FROM issues i
WHERE a.project_id IS NULL AND a.entity_type='ISSUE' AND a.entity_id=i.issue_id AND i.created_at<=a.created_at;
UPDATE audit_logs a SET project_id=s.project_id FROM sprints s
WHERE a.project_id IS NULL AND a.entity_type='SPRINT' AND a.entity_id=s.sprint_id AND s.created_at<=a.created_at;
UPDATE audit_logs a SET project_id=b.project_id FROM boards b
WHERE a.project_id IS NULL AND a.entity_type='BOARD' AND a.entity_id=b.board_id AND b.created_at<=a.created_at;
UPDATE audit_logs a SET project_id=i.project_id FROM issue_comments c JOIN issues i ON i.issue_id=c.issue_id
WHERE a.project_id IS NULL AND a.entity_type='COMMENT' AND a.entity_id=c.comment_id AND c.created_at<=a.created_at AND i.created_at<=a.created_at;
UPDATE audit_logs a SET project_id=i.project_id FROM issue_attachments f JOIN issues i ON i.issue_id=f.issue_id
WHERE a.project_id IS NULL AND a.entity_type='ATTACHMENT' AND a.entity_id=f.attachment_id AND f.uploaded_at<=a.created_at AND i.created_at<=a.created_at;
UPDATE audit_logs a SET project_id=i.project_id FROM work_logs w JOIN issues i ON i.issue_id=w.issue_id
WHERE a.project_id IS NULL AND a.entity_type='WORKLOG' AND a.entity_id=w.worklog_id AND w.logged_at<=a.created_at AND i.created_at<=a.created_at;
