-- Không chọn/xóa cấu hình legacy tự động. Dừng trước thay đổi nếu còn nhiều default hoặc cạnh trùng.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM project_issue_types WHERE is_default=true GROUP BY project_id HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Multiple default issue types found; review data before migration';
 END IF;
 IF EXISTS(SELECT 1 FROM workflow_rules GROUP BY from_status_id,to_status_id HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Duplicate workflow edges found; review data before migration';
 END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_default_type ON project_issue_types(project_id) WHERE is_default=true;
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_edge ON workflow_rules(from_status_id,to_status_id);
COMMIT;
