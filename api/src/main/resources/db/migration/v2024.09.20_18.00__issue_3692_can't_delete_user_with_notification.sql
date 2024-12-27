ALTER TABLE pipeline.user_notification_resource DROP CONSTRAINT user_notification_resource_notification_id_fkey;
ALTER TABLE pipeline.user_notification_resource ADD CONSTRAINT user_notification_resource_notification_id_fkey
    FOREIGN KEY (notification_id)
    REFERENCES pipeline.user_notification(id)
    ON DELETE CASCADE;
