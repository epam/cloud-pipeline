CREATE TABLE IF NOT EXISTS pipeline.pipeline_run_metrics (
  run_id BIGINT NOT NULL,
  metric_type VARCHAR(128) NOT NULL,
  max_value NUMERIC,
  avg_value NUMERIC,
  capacity NUMERIC,
  PRIMARY KEY (run_id, metric_type),
  CONSTRAINT run_id_fk FOREIGN KEY (run_id) REFERENCES pipeline.pipeline_run (run_id)
);