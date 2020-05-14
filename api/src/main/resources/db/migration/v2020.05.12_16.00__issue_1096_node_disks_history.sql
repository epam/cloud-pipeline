CREATE TABLE IF NOT EXISTS pipeline.node_disk
(
    node_id      TEXT                     NOT NULL,
    size         INT                      NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL
);
