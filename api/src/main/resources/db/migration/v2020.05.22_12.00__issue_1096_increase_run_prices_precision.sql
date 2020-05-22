ALTER TABLE pipeline.pipeline_run ALTER COLUMN compute_price_per_hour TYPE numeric(100, 5) USING compute_price_per_hour::numeric(100, 5);
ALTER TABLE pipeline.pipeline_run ALTER COLUMN disk_price_per_hour TYPE numeric(100, 5) USING disk_price_per_hour::numeric(100, 5);
