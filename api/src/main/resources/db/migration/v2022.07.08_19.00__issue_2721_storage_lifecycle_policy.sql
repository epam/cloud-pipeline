CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule(
    id SERIAL PRIMARY KEY,
    datastorage_id BIGINT NOT NULL,
    path_glob TEXT NOT NULL,
    object_glob TEXT DEFAULT NULL,
    transition_method TEXT NOT NULL,
    transitions_json TEXT NOT NULL,
    prolongations_json TEXT NOT NULL,
    notification_json TEXT DEFAULT NULL
);