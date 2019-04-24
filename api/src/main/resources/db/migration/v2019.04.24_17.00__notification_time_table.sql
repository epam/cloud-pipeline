CREATE TABLE IF NOT EXISTS pipeline.notification_timestamp (
  run_id BIGINT NOT NULL,
  notification_type TEXT NOT NULL,
  timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (run_id, notification_type),
  CONSTRAINT run_id_fk FOREIGN KEY (run_id) REFERENCES pipeline.pipeline_run (run_id)
);