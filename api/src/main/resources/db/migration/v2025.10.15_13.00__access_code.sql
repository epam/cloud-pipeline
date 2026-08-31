CREATE TABLE IF NOT EXISTS pipeline.access_code (
    id SERIAL PRIMARY KEY,
    user_name TEXT NOT NULL,
    code TEXT NOT NULL,
    code_challenge TEXT NOT NULL,
    code_challenge_method TEXT NOT NULL,
    created timestamp WITHOUT TIME ZONE NOT NULL,
    issued BOOLEAN DEFAULT FALSE  NOT NULL,
    CONSTRAINT unique_code UNIQUE(code),
    CONSTRAINT unique_code_challenge UNIQUE(code_challenge)
);
