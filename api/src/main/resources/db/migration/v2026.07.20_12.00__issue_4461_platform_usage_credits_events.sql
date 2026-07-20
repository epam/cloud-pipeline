CREATE TABLE IF NOT EXISTS pipeline.usage_credits_update_event (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    rule_id       BIGINT      REFERENCES pipeline.usage_credits_update_rule(id) ON DELETE SET NULL,
    entity_class  TEXT,
    entity_id     BIGINT,
    incident_type TEXT        NOT NULL,
    value         INTEGER     NOT NULL,
    message       TEXT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX usage_credits_update_event_dedup_idx
    ON pipeline.usage_credits_update_event (rule_id, entity_class, entity_id, incident_type)
    WHERE rule_id IS NOT NULL AND entity_class IS NOT NULL AND entity_id IS NOT NULL;

CREATE INDEX usage_credits_update_event_user_idx ON pipeline.usage_credits_update_event (user_id);
CREATE INDEX usage_credits_update_event_rule_idx ON pipeline.usage_credits_update_event (rule_id);
