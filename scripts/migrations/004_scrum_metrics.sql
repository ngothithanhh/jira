BEGIN;
CREATE TABLE IF NOT EXISTS sprint_metrics (
 metric_id bigserial PRIMARY KEY, sprint_id integer NOT NULL UNIQUE REFERENCES sprints(sprint_id),
 origin varchar(255) NOT NULL, started_at timestamp NOT NULL, finished_at timestamp,
 committed_points bigint NOT NULL, committed_count bigint NOT NULL, completed_points bigint, completed_count bigint,
 last_total bigint NOT NULL DEFAULT 0, last_remaining bigint NOT NULL DEFAULT 0, last_count bigint NOT NULL DEFAULT 0,
 last_remaining_count bigint NOT NULL DEFAULT 0, last_invalid_count bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sprint_metric_points (
 point_id bigserial PRIMARY KEY, metric_id bigint NOT NULL REFERENCES sprint_metrics(metric_id),
 recorded_at timestamp NOT NULL, total_points bigint NOT NULL, remaining_points bigint NOT NULL,
 issue_count bigint NOT NULL, remaining_count bigint NOT NULL, invalid_estimate_count bigint NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_sprint_metric_points_order ON sprint_metric_points(metric_id,recorded_at,point_id);
COMMIT;
