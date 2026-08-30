ALTER TABLE pipeline.node_schedule_entry ALTER COLUMN from_time DROP NOT NULL;
ALTER TABLE pipeline.node_schedule_entry ALTER COLUMN to_time DROP NOT NULL;
