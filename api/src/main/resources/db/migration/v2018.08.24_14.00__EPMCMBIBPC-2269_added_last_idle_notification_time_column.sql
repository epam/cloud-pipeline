-- a field to record last time the notification on idle run was issued
ALTER TABLE pipeline.pipeline_run ADD COLUMN last_idle_notification_time TIMESTAMP WITH TIME ZONE;