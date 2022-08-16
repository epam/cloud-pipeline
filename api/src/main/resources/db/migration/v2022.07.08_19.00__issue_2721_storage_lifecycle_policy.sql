CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule(
    id SERIAL PRIMARY KEY,
    datastorage_id BIGINT NOT NULL REFERENCES pipeline.datastorage (datastorage_id),
    path_glob TEXT NOT NULL,
    object_glob TEXT DEFAULT NULL,
    transition_criterion_json TEXT DEFAULT NULL,
    transition_method TEXT NOT NULL,
    transitions_json TEXT NOT NULL,
    notification_json TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule_prolongation(
    id SERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES pipeline.datastorage_lifecycle_rule (id),
    path TEXT DEFAULT NULL,
    prolonged_date TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    days BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.datastorage_lifecycle_rule_execution(
    id SERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES pipeline.datastorage_lifecycle_rule (id),
    path TEXT NOT NULL,
    updated TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    status TEXT NOT NULL,
    storage_class TEXT NOT NULL
);