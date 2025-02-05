CREATE TABLE pipeline.pipeline_run_result (
    run_id INTEGER REFERENCES pipeline.pipeline_run(run_id) NOT NULL,
    name VARCHAR NOT NULL,
    pattern VARCHAR NOT NULL,
    path VARCHAR NOT NULL,
    CONSTRAINT pipeline_run_result_constrain UNIQUE(run_id, name, pattern, path)
);
