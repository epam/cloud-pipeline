CREATE TABLE IF NOT EXISTS pipeline.quota (
    id SERIAL PRIMARY KEY,
    quota_group TEXT NOT NULL,
    type TEXT,
    period TEXT,
    subject TEXT,
    value DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quota_action (
    id SERIAL PRIMARY KEY,
    quota_id BIGINT REFERENCES pipeline.quota(id),
    threshold DOUBLE PRECISION NOT NULL,
    actions TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quota_entity_recipients (
    quota_entity_id BIGINT REFERENCES pipeline.quota(id),
    principal BOOLEAN NOT NULL,
    name TEXT NOT NULL
);
