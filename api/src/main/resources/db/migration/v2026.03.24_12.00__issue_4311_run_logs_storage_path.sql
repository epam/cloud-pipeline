ALTER TABLE pipeline.pipeline_run ADD COLUMN IF NOT EXISTS logs_storage_path TEXT;
ALTER TABLE pipeline.archive_run ADD COLUMN IF NOT EXISTS logs_storage_path TEXT;
