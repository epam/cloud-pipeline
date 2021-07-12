CREATE TABLE pipeline.pipeline_run_service_url (
    id SERIAL PRIMARY KEY,
    pipeline_run_id integer NOT NULL,
    region text NOT NULL,
    service_url text NOT NULL,
    CONSTRAINT pipeline_run_service_url_unique UNIQUE (pipeline_run_id, region),
    CONSTRAINT pipeline_run_service_url_fkey FOREIGN KEY (pipeline_run_id) REFERENCES pipeline.pipeline_run (RUN_ID)
);
ALTER TABLE pipeline.pipeline_run DROP COLUMN SERVICE_URL;
