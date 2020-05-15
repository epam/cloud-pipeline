CREATE TABLE IF NOT EXISTS pipeline.node_disk
(
    node_id      TEXT                     NOT NULL,
    size         INT                      NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE pipeline.pipeline_run ADD compute_price_per_hour NUMERIC(2) DEFAULT 0;
ALTER TABLE pipeline.pipeline_run ADD disk_price_per_hour NUMERIC(2) DEFAULT 0;
