CREATE TABLE IF NOT EXISTS pipeline.quota (
    id SERIAL PRIMARY KEY,
    quota_group TEXT NOT NULL,
    type TEXT NOT NULL,
    period TEXT,
    name TEXT,
    value BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quota_action (
    id SERIAL PRIMARY KEY,
    quota_id BIGINT REFERENCES pipeline.quota(id),
    threshold INTEGER NOT NULL,
    actions TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quotas_users (
    quota_id BIGINT REFERENCES pipeline.quota(id),
    user_id BIGINT REFERENCES pipeline.user(id)
);
