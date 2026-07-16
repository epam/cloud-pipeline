CREATE TABLE IF NOT EXISTS pipeline.usage_credits_update_rule (
    id                 BIGSERIAL                   PRIMARY KEY,
    name               TEXT        NOT NULL,
    description        TEXT,
    rule_type          TEXT        NOT NULL        DEFAULT 'RUN_STATE',
    statement          JSONB       NOT NULL,
    exclude            JSONB,
    action_type        TEXT        NOT NULL,
    action_value       INTEGER     NOT NULL,
    action_message     TEXT,
    per_incident       BOOLEAN     NOT NULL        DEFAULT FALSE,
    created_date       TIMESTAMPTZ NOT NULL        DEFAULT now(),
    modified_date      TIMESTAMPTZ NOT NULL        DEFAULT now()
);
