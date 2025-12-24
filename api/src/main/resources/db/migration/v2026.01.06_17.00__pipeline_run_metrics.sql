CREATE TABLE IF NOT EXISTS pipeline.pipeline_run_metrics (
  run_id BIGINT NOT NULL,
  metric_type VARCHAR(128) NO NULL,
  max_value SMALLINT,
  avg_value SMALLINT,
  capacity BIGINT,
  PRIMARY KEY (run_id, metric_type),
  CONSTRAINT run_id_fk FOREIGN KEY (run_id) REFERENCES pipeline.pipeline_run (run_id)
);