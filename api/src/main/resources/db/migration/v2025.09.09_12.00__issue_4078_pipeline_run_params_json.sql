ALTER TABLE pipeline.pipeline_run ADD parameters_json JSONB;
CREATE INDEX parameters_json_index_jsonb ON pipeline.pipeline_run USING GIN (parameters_json);

ALTER TABLE pipeline.archive_run ADD parameters_json JSONB;
CREATE INDEX archive_parameters_json_index_jsonb ON pipeline.archive_run USING GIN (parameters_json);