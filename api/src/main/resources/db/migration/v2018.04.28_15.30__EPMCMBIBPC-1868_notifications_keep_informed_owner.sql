ALTER TABLE pipeline.notification_settings ADD COLUMN keep_informed_owner BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pipeline.notification_queue ALTER COLUMN to_user_id DROP NOT NULL;