CREATE TABLE IF NOT EXISTS pipeline.applied_quota (
    id SERIAL PRIMARY KEY,
    action_id BIGINT REFERENCES pipeline.quota_action(id),
    expense DOUBLE PRECISION NOT NULL,
    from_date TIMESTAMP WITH TIME ZONE NOT NULL,
    to_date TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_date TIMESTAMP WITH TIME ZONE NOT NULL
);
