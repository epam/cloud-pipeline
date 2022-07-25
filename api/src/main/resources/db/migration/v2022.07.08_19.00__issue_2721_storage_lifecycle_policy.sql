CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule(
    id SERIAL PRIMARY KEY,
    parent_rule_id BIGINT DEFAULT NULL,
    datastorage_id BIGINT NOT NULL,
    path_root TEXT NOT NULL,
    path_glob TEXT NOT NULL,
    enabled boolean NOT NULL,
    transitions TEXT NOT NULL
);
