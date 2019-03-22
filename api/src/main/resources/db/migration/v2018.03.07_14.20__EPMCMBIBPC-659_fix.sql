ALTER TABLE pipeline.tool_version_scan DROP CONSTRAINT IF EXISTS tool_layers_pkey;
ALTER TABLE pipeline.tool_version_scan ALTER COLUMN layer_reference DROP NOT NULL;