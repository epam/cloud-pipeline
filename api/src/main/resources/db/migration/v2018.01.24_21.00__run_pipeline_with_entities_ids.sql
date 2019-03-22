ALTER TABLE pipeline.pipeline_run ADD entities_ids BIGINT[];
CREATE INDEX pipeline_run_with_entities ON pipeline.pipeline_run USING GIN (entities_ids);