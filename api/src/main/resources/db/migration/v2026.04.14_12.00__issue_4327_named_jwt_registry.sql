-- Named JWT registry and revocation (issue #4327): final schema for new deployments.

CREATE TABLE IF NOT EXISTS pipeline.jwt_named_token (
    jti VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    token_name VARCHAR(512) NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_jwt_named_token PRIMARY KEY (jti),
    CONSTRAINT fk_jwt_named_token_user FOREIGN KEY (user_id) REFERENCES pipeline.user (id),
    CONSTRAINT fk_jwt_named_token_created_by FOREIGN KEY (created_by) REFERENCES pipeline.user (id)
);

CREATE INDEX IF NOT EXISTS idx_jwt_named_token_user_id ON pipeline.jwt_named_token (user_id);

CREATE TABLE IF NOT EXISTS pipeline.jwt_token_revocation (
    jti VARCHAR(128) NOT NULL,
    revoked_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_jwt_token_revocation PRIMARY KEY (jti)
);
