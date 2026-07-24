CREATE TABLE IF NOT EXISTS pipeline.usage_credits_user_balance (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE REFERENCES pipeline.user(id),
    current_value INTEGER      NOT NULL,
    modified_date TIMESTAMPTZ  NOT NULL DEFAULT now()
);
