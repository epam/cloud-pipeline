ALTER TABLE pipeline.pipeline_run ADD last_notification_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE pipeline.notification_queue ADD COLUMN to_user_id BIGINT NOT NULL;
ALTER TABLE pipeline.notification_queue ADD CONSTRAINT notification_queue_to_user_id_fk FOREIGN KEY (to_user_id) REFERENCES pipeline.user (id);
ALTER TABLE pipeline.notification_template ADD COLUMN name VARCHAR(1024);