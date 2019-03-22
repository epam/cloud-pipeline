ALTER TABLE pipeline.pipeline_run ADD commit_status BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE pipeline.pipeline_run ADD last_change_commit_time TIMESTAMP WITH TIME ZONE;
UPDATE pipeline.pipeline_run SET last_change_commit_time = start_date;
ALTER TABLE pipeline.pipeline_run ALTER COLUMN last_change_commit_time SET NOT NULL;