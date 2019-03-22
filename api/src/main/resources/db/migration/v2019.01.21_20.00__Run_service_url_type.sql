ALTER TABLE pipeline.pipeline_run
  ALTER COLUMN service_url TYPE text USING service_url::text;