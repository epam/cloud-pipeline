ALTER TABLE pipeline.pipeline_run ADD node_pool_id BIGINT;
CREATE TABLE IF NOT EXISTS pipeline.node_pool_usage(
    id SERIAL PRIMARY KEY,
    log_date TIMESTAMP NOT NULL,
    node_pool_id BIGINT NOT NULL,
    total_nodes_count INT,
    occupied_nodes_count INT
);
