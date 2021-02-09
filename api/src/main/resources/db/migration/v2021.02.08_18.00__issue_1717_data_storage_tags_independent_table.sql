DROP TABLE pipeline.datastorage_tag;

CREATE TABLE IF NOT EXISTS pipeline.datastorage_root (
    datastorage_root_id      SERIAL                   NOT NULL,
    datastorage_root_path    TEXT                     NOT NULL,
    PRIMARY KEY (datastorage_root_id),
    UNIQUE (datastorage_root_path)
);

INSERT INTO pipeline.datastorage_root(datastorage_root_path)
SELECT DISTINCT regexp_replace(path, '/.*$', '')
FROM pipeline.datastorage;

CREATE OR REPLACE FUNCTION create_datastorage_root_from_datastorage_function()
RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO pipeline.datastorage_root(datastorage_root_path)
    VALUES (regexp_replace(new.path, '/.*$', ''))
    ON CONFLICT (datastorage_root_path)
    DO NOTHING;
    RETURN new;
END;
$BODY$
language plpgsql;

CREATE TRIGGER create_datastorage_root_trigger
     AFTER INSERT ON pipeline.datastorage
     FOR EACH ROW
     EXECUTE PROCEDURE create_datastorage_root_from_datastorage_function();

CREATE TABLE IF NOT EXISTS pipeline.datastorage_tag (
    datastorage_root_id      BIGINT                   NOT NULL,
    datastorage_path         TEXT                     NOT NULL,
    datastorage_version      TEXT                     NOT NULL,
    tag_key                  TEXT                     NOT NULL,
    tag_value                TEXT                     NOT NULL,
    created_date             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (datastorage_root_id, datastorage_path, datastorage_version, tag_key),
    CONSTRAINT datastorage_tag_datastorage_root_id
        FOREIGN KEY (datastorage_root_id)
        REFERENCES pipeline.datastorage_root (datastorage_root_id)
);

CREATE INDEX datastorage_tag_path ON pipeline.datastorage_tag (datastorage_root_id, datastorage_path);
