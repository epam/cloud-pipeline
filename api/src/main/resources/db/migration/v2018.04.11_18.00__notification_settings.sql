CREATE SEQUENCE pipeline.s_notification_settings START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.notification_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    threshold BIGINT NOT NULL,
    resend_delay BIGINT NOT NULL,
    informed_user_ids BIGINT[] NOT NULL,
    keep_informed_admins BOOLEAN NOT NULL
);

ALTER TABLE pipeline.notification_template DROP COLUMN notification_type;
