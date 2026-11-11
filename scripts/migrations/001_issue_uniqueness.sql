-- Chạy bằng psql trên DB triển khai sau khi sao lưu. Không sửa/xóa dữ liệu trùng tự động.
BEGIN;
LOCK TABLE issues IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM issues GROUP BY project_id, issue_number HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM issues GROUP BY issue_key HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'issues contains duplicate project/number or issue_key; resolve duplicates before migration';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'issues'::regclass AND conname = 'uk_issues_project_number') THEN
        ALTER TABLE issues ADD CONSTRAINT uk_issues_project_number UNIQUE (project_id, issue_number);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'issues'::regclass AND conname = 'uk_issues_issue_key') THEN
        ALTER TABLE issues ADD CONSTRAINT uk_issues_issue_key UNIQUE (issue_key);
    END IF;
END $$;
COMMIT;
