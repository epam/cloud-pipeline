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
    time_window        INTEGER,
    created_date       TIMESTAMPTZ NOT NULL        DEFAULT now(),
    modified_date      TIMESTAMPTZ NOT NULL        DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pipeline.usage_credits_update_event (
    id            TEXT        PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id       BIGINT      NOT NULL,
    rule_id       BIGINT      REFERENCES pipeline.usage_credits_update_rule(id) ON DELETE SET NULL,
    entity_class  TEXT,
    entity_id     BIGINT,
    incident_type TEXT        NOT NULL,
    value         INTEGER     NOT NULL,
    message       TEXT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX usage_credits_update_event_user_idx ON pipeline.usage_credits_update_event (user_id);
CREATE INDEX usage_credits_update_event_rule_idx ON pipeline.usage_credits_update_event (rule_id);

CREATE TABLE IF NOT EXISTS pipeline.usage_credits_user_balance (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE REFERENCES pipeline.user(id),
    current_value INTEGER      NOT NULL,
    modified_date TIMESTAMPTZ  NOT NULL DEFAULT now()
);
