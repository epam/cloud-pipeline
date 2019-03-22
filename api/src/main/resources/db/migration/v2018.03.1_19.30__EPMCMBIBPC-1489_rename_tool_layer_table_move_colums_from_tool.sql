ALTER TABLE pipeline.tool_layers RENAME TO tool_version_scan;
ALTER TABLE pipeline.tool_version_scan ADD COLUMN success_scan_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE pipeline.tool_version_scan ADD COLUMN scan_status INTEGER;
ALTER TABLE pipeline.tool_version_scan ADD COLUMN scan_date TIMESTAMP WITH TIME ZONE;

ALTER TABLE pipeline.tool DROP COLUMN scanned;
ALTER TABLE pipeline.tool DROP COLUMN scan_date;
ALTER TABLE pipeline.tool DROP COLUMN scan_status;
