CREATE TABLE IF NOT EXISTS pipeline.online_users (
    id SERIAL PRIMARY KEY,
    log_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.online_users_entity_user_ids (
    online_users_entity_id BIGINT REFERENCES pipeline.online_users(id),
    user_ids BIGINT
);
