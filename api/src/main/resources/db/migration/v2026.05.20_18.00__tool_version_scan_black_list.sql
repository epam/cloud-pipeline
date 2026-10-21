ALTER TABLE pipeline.tool_version_scan ADD COLUMN IF NOT EXISTS black_list BOOLEAN DEFAULT FALSE;
