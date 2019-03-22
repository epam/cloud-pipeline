CREATE TABLE IF NOT EXISTS pipeline.contextual_preference (
    name                     TEXT                     NOT NULL,
    value                    TEXT                     NOT NULL,
    type                     BIGINT                   NOT NULL,
    created_date             TIMESTAMP WITH TIME ZONE NOT NULL,
    level                    BIGINT                   NOT NULL,
    resource_id              TEXT                     NOT NULL,
    PRIMARY KEY (name, level, resource_id)
);
