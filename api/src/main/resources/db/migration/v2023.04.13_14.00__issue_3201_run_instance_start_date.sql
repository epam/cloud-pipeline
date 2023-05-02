ALTER TABLE pipeline.pipeline_run ADD COLUMN node_start_date TIMESTAMP WITH TIME ZONE;
UPDATE pipeline.pipeline_run SET node_start_date = start_date;
