-- a field to record starting time the notification on idle run
ALTER TABLE pipeline.pipeline_run ADD COLUMN prolonged_at_time TIMESTAMP WITH TIME ZONE;
UPDATE pipeline.pipeline_run SET prolonged_at_time = start_date;