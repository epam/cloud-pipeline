CREATE TABLE IF NOT EXISTS pipeline.dts_transfer_task (
    id SERIAL PRIMARY KEY,
    registry_id BIGINT REFERENCES pipeline.dts_registry(id),
    status BIGINT NOT NULL,
    source_type TEXT NOT NULL,
    source_path TEXT NOT NULL,
    destination_type TEXT NOT NULL,
    destination_path TEXT NOT NULL,
    created TIMESTAMP,
    started TIMESTAMP,
    finished TIMESTAMP,
    reason TEXT,
    user_name TEXT,
    delete_source BOOLEAN
);

CREATE TABLE IF NOT EXISTS pipeline.dts_included (
    id SERIAL PRIMARY KEY,
    task_id BIGINT REFERENCES pipeline.dts_transfer_task(id),
    included TEXT NOT NULL
);
