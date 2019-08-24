CREATE TABLE IF NOT EXISTS pipeline.group_status (
    group_name TEXT NOT NULL PRIMARY KEY,
    blocked BOOLEAN DEFAULT FALSE
);
