ALTER TABLE pipeline.usage_credits_update_rule
    DROP COLUMN IF EXISTS per_incident;

ALTER TABLE pipeline.usage_credits_update_rule
    ADD COLUMN IF NOT EXISTS time_window INTEGER;
