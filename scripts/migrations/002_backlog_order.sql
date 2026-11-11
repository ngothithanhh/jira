-- Nullable giữ nguyên dữ liệu cũ; service xếp việc chưa có rank theo priority và issue_number.
BEGIN;
ALTER TABLE issues ADD COLUMN IF NOT EXISTS backlog_position integer;
ALTER TABLE sprint_issues ADD COLUMN IF NOT EXISTS position integer;
COMMIT;
