CREATE INDEX IF NOT EXISTS pipeline_run_tags_index ON pipeline.pipeline_run USING GIN (tags);
