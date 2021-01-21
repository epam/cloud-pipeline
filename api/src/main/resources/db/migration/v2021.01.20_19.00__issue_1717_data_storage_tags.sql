CREATE TABLE IF NOT EXISTS pipeline.datastorage_tag (
    datastorage_id           BIGINT                   NOT NULL,
    datastorage_path         TEXT                     NOT NULL,
    datastorage_version      TEXT                     NOT NULL,
    tag_key                  TEXT                     NOT NULL,
    tag_value                TEXT                     NOT NULL,
    created_date             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (datastorage_id, datastorage_path, datastorage_version, tag_key),
    CONSTRAINT datastorage_tag_datastorage_id
        FOREIGN KEY (datastorage_id)
        REFERENCES pipeline.datastorage (datastorage_id)
);
