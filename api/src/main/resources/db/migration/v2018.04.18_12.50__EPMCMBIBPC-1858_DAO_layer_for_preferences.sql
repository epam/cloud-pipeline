CREATE TABLE pipeline.preference (
    preference_name TEXT    NOT NULL PRIMARY KEY,
    created_date    TIMESTAMP WITH TIME ZONE NOT NULL,
    value           TEXT,
    preference_group  TEXT,
    description     TEXT,
    visible         BOOLEAN DEFAULT TRUE  NOT NULL,
    preference_type BIGINT  DEFAULT 0   NOT NULL
);