-- a field to record last time the notification on high network consuming run was issued
ALTER TABLE pipeline.pipeline_run ADD COLUMN last_network_consumption_notification_time TIMESTAMP WITH TIME ZONE;
