CREATE SEQUENCE IF NOT EXISTS pipeline.s_routing_rule_id START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.nat_routing_rule_queue (
    route_id BIGINT NOT NULL,
    external_name TEXT NOT NULL,
    external_ip TEXT NOT NULL,
    external_port INTEGER NOT NULL,
    status TEXT NOT NULL,
    internal_name TEXT,
    internal_ip TEXT,
    internal_port INTEGER,
    last_update_time TIMESTAMP,
    last_error_time TIMESTAMP,
    last_error_message TEXT,
    CONSTRAINT unique_external_resource_description UNIQUE (external_name,external_port)
);
