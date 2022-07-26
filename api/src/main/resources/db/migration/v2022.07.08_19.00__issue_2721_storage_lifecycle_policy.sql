CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule_template(
    id SERIAL PRIMARY KEY,
    description TEXT DEFAULT NULL,
    datastorage_id BIGINT NOT NULL,
    enabled boolean NOT NULL,
    path_root TEXT NOT NULL,
    object_glob TEXT DEFAULT NULL,
    transition_method TEXT NOT NULL,
    transitions_json TEXT NOT NULL,
    notification_json TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule(
    id SERIAL PRIMARY KEY,
    template_id BIGINT REFERENCES pipeline.datastorage_lifecycle_rule_template (id),
    datastorage_id BIGINT NOT NULL,
    prolonged_date TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    path_root TEXT NOT NULL,
    object_glob TEXT DEFAULT NULL,
    transition_method TEXT NOT NULL,
    transitions_json TEXT NOT NULL,
    notification_json TEXT DEFAULT NULL
);