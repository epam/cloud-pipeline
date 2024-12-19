ALTER TABLE pipeline.cloud_region ADD COLUMN cluster_include BOOL DEFAULT FALSE;
ALTER TABLE pipeline.cloud_region ADD COLUMN cluster_state_properties TEXT;
