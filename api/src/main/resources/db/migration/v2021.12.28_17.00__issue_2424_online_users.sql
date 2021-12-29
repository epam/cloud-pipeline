CREATE TABLE IF NOT EXISTS pipeline.online_users (
    id SERIAL PRIMARY KEY,
    user_ids TEXT,
    log_date TIMESTAMP WITH TIME ZONE NOT NULL
);
