ALTER TABLE pipeline.user_notification ADD type VARCHAR;

CREATE TABLE pipeline.user_notification_entity (
    id SERIAL PRIMARY KEY,
    notification_id INTEGER REFERENCES pipeline.user_notification(id),
    entity_class VARCHAR NOT NULL,
    entity_id INTEGER NOT NULL,
    storage_path VARCHAR,
    storage_rule_id INTEGER
);
