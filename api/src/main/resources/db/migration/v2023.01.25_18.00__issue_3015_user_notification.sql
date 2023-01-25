CREATE TABLE pipeline.user_notification (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES pipeline.user(id),
    subject VARCHAR NOT NULL,
    text VARCHAR NOT NULL,
    is_read BOOLEAN NOT NULL,
    created_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    read_date TIMESTAMP WITHOUT TIME ZONE
);
