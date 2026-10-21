ALTER TABLE pipeline.pipeline_run ADD COLUMN IF NOT EXISTS initialized BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE pipeline.pipeline_run r
SET initialized = TRUE
WHERE EXISTS (
    SELECT 1 FROM pipeline.pipeline_run_log l
    WHERE l.run_id = r.run_id
      AND l.task_name = 'InitializeEnvironment'
      AND l.status = 0);
