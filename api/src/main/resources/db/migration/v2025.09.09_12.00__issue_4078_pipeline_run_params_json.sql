ALTER TABLE pipeline.pipeline_run ADD parameter_json JSONB;
CREATE INDEX parameter_json_index_jsonb ON pipeline.pipeline_run USING GIN (parameter_json);

ALTER TABLE pipeline.archive_run ADD parameter_json JSONB;
CREATE INDEX archive_parameter_json_index_jsonb ON pipeline.archive_run USING GIN (parameter_json);