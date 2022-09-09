CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_restore_action(
    id SERIAL PRIMARY KEY,
    datastorage_id BIGINT NOT NULL REFERENCES pipeline.datastorage (datastorage_id),
    user_actor_id BIGINT NOT NULL REFERENCES pipeline.user (id),
    path TEXT NOT NULL,
    type TEXT NOT NULL,
    restore_versions BOOLEAN DEFAULT FALSE,
    restore_mode TEXT NOT NULL,
    days BIGINT NOT NULL,
    updated TIMESTAMP WITH TIME ZONE NOT NULL,
    started TIMESTAMP WITH TIME ZONE NOT NULL,
    status TEXT NOT NULL,
    restored_till TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    notification_json TEXT NOT NULL
);
