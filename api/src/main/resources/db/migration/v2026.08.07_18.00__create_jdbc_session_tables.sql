CREATE TABLE IF NOT EXISTS pipeline.spring_3_session(
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100),
    CONSTRAINT spring_3_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_3_session_ix1 ON pipeline.spring_3_session USING btree(session_id ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS spring_3_session_ix2 ON pipeline.spring_3_session USING btree(expiry_time ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS spring_3_session_ix3 ON pipeline.spring_3_session USING btree(principal_name ASC NULLS LAST);

CREATE TABLE IF NOT EXISTS pipeline.spring_3_session_attributes(
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    CONSTRAINT spring_3_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_3_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES pipeline.spring_3_session (primary_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);