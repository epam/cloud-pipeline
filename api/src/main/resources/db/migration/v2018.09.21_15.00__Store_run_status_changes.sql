CREATE TABLE pipeline.run_status_change (
  run_id  BIGINT   NOT NULL,
  status  BIGINT  NOT NULL,
  date  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT status_run_id_id_fkey  FOREIGN KEY (run_id) REFERENCES PIPELINE.PIPELINE_RUN (run_id)
);