ALTER TABLE pipeline.pipeline_run
    DROP COLUMN last_notification_time,
    DROP COLUMN last_idle_notification_time,
    DROP COLUMN last_network_consumption_notification_time;

ALTER TABLE pipeline.archive_run
    DROP COLUMN last_notification_time,
    DROP COLUMN last_idle_notification_time,
    DROP COLUMN last_network_consumption_notification_time;
