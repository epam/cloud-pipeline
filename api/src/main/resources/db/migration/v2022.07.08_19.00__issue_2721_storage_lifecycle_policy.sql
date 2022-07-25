CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_policy(
    id SERIAL PRIMARY KEY,
    description TEXT NOT NULL,
    datastorage_id BIGINT NOT NULL,
    enabled boolean NOT NULL,
    path_root TEXT NOT NULL,
    object_glob TEXT NOT NULL,
    transitions_json TEXT NOT NULL,
    notification_json TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule(
    id SERIAL PRIMARY KEY,
    policy_id BIGINT REFERENCES pipeline.datastorage_lifecycle_policy (id),
    datastorage_id BIGINT NOT NULL,
    path_root TEXT NOT NULL,
    object_glob TEXT NOT NULL,
    transitions_son TEXT NOT NULL
);