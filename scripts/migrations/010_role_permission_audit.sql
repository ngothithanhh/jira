-- Giữ lịch sử hiện có; ROLE dùng ID user_roles, không gán nhầm thành users.
BEGIN;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_entity_type_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check
 CHECK (entity_type IN ('USER','PROJECT','ISSUE','SPRINT','BOARD','COMMENT','ATTACHMENT','WORKLOG','ROLE'));
COMMIT;
