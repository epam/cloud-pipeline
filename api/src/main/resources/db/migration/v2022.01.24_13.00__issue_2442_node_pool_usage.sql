ALTER TABLE pipeline.pipeline_run ADD node_pool_id BIGINT;
CREATE TABLE IF NOT EXISTS pipeline.node_pool_usage(
    id SERIAL PRIMARY KEY,
    log_date TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS pipeline.node_pool_usage_record(
    id SERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES pipeline.node_pool_usage(id),
    node_pool_id BIGINT NOT NULL,
    total_nodes INT,
    nodes_in_use INT
);
