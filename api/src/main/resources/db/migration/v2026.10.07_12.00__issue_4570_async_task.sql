CREATE TABLE IF NOT EXISTS pipeline.async_task (
    id             TEXT        PRIMARY KEY DEFAULT gen_random_uuid()::text,
    type           TEXT        NOT NULL,
    owner          TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    params         JSONB,
    result         JSONB,
    error_message  TEXT,
    created        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started        TIMESTAMPTZ,
    finished       TIMESTAMPTZ,
    status_changed TIMESTAMPTZ NOT NULL DEFAULT now()
);