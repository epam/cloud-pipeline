ALTER TABLE pipeline.notification_settings ALTER COLUMN threshold DROP NOT NULL;
ALTER TABLE pipeline.notification_settings ALTER COLUMN resend_delay DROP NOT NULL;
ALTER TABLE pipeline.notification_settings ALTER COLUMN informed_user_ids DROP NOT NULL;
ALTER TABLE pipeline.notification_settings ADD CONSTRAINT notification_settings_template_id_fk FOREIGN KEY (template_id) REFERENCES notification_template (id);