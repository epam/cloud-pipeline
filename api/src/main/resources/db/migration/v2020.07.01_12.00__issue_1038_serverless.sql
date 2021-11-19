CREATE SEQUENCE pipeline.s_stop_serverless_run START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS pipeline.stop_serverless_run (
    id BIGINT NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES pipeline.pipeline_run(run_id),
    last_update TIMESTAMP WITH TIME ZONE NOT NULL
);
