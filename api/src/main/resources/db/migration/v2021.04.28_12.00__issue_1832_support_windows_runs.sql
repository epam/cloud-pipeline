ALTER TABLE pipeline.tool_version
ADD COLUMN IF NOT EXISTS platform TEXT NOT NULL DEFAULT 'linux';

ALTER TABLE pipeline.pipeline_run
ADD COLUMN IF NOT EXISTS platform TEXT NOT NULL DEFAULT 'linux';

ALTER TABLE pipeline.pipeline_run
ADD COLUMN IF NOT EXISTS node_platform TEXT NOT NULL DEFAULT 'linux';
