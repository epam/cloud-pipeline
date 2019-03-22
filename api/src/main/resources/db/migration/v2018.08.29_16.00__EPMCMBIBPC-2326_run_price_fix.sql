ALTER TABLE pipeline.pipeline_run ALTER COLUMN price_per_hour TYPE numeric(100,2) USING price_per_hour::numeric(100,2);
