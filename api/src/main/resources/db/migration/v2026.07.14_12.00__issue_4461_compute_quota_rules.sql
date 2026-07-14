CREATE TABLE IF NOT EXISTS pipeline.compute_quota_rule (
    id                 BIGSERIAL                   PRIMARY KEY,
    name               TEXT        NOT NULL,
    description        TEXT,
    strategy_type      TEXT        NOT NULL        DEFAULT 'RUN_STATE',
    filter_expression  JSONB       NOT NULL,
    exclude_expression JSONB,
    action_type        TEXT        NOT NULL,
    action_value       INTEGER     NOT NULL,
    action_message     TEXT,
    per_incident       BOOLEAN     NOT NULL        DEFAULT FALSE,
    created_date       TIMESTAMPTZ NOT NULL        DEFAULT now(),
    modified_date      TIMESTAMPTZ NOT NULL        DEFAULT now()
);
