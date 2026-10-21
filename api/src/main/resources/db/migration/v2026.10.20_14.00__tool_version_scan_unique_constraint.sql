DELETE FROM pipeline.tool_version_scan t
WHERE t.id NOT IN (
    SELECT DISTINCT ON (tool_id, version) id
    FROM pipeline.tool_version_scan
    ORDER BY tool_id, version, scan_date DESC NULLS LAST, id DESC
);

ALTER TABLE pipeline.tool_version_scan ADD CONSTRAINT tool_id_version_unique_key UNIQUE (tool_id, version);
