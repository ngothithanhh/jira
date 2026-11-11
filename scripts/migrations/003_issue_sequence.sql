BEGIN;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS issue_sequence integer NOT NULL DEFAULT 0;
-- Không hạ counter khi chạy lại hoặc sau khi số lớn nhất đã bị xóa.
UPDATE projects p SET issue_sequence = greatest(p.issue_sequence,coalesce((SELECT max(i.issue_number) FROM issues i WHERE i.project_id = p.project_id),0));
COMMIT;
