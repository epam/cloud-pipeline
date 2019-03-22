CREATE TABLE pipeline.restart_run (
  parent_run_id  BIGINT   NOT NULL,
  restarted_run_id  BIGINT  NOT NULL,
  date  TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(parent_run_id, restarted_run_id),
  CONSTRAINT  parent_run_id_fkey  FOREIGN KEY (parent_run_id) REFERENCES PIPELINE.PIPELINE_RUN (run_id)
);
CREATE UNIQUE INDEX restart_run_index ON pipeline.restart_run (parent_run_id, restarted_run_id);