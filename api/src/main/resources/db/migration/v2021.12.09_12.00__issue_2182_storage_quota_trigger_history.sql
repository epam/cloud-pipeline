CREATE TABLE IF NOT EXISTS pipeline.last_triggered_storage_quota (
    storage_id BIGINT NOT NULL PRIMARY KEY,
    quota_value DOUBLE PRECISION NOT NULL,
    quota_type TEXT NOT NULL,
    actions JSONB NOT NULL,
    CONSTRAINT datastorage_id_fk FOREIGN KEY (storage_id)
        REFERENCES pipeline.datastorage(datastorage_id) ON DELETE CASCADE
);
