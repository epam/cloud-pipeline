DROP SEQUENCE pipeline.s_notification_settings;
DROP SEQUENCE pipeline.s_notification_templates;

ALTER TABLE pipeline.notification_settings DROP CONSTRAINT IF EXISTS notification_settings_template_id_fk;

UPDATE pipeline.notification_template
SET id = (
   SELECT id
   FROM pipeline.notification_settings
   WHERE pipeline.notification_settings.template_id = pipeline.notification_template.id
);

UPDATE pipeline.notification_settings
SET template_id = id;

ALTER TABLE pipeline.notification_settings ADD CONSTRAINT notification_settings_template_id_fk FOREIGN KEY (template_id) REFERENCES pipeline.notification_template (id);
