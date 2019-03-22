ALTER TABLE pipeline.dts_registry ADD name text NULL;
ALTER TABLE pipeline.dts_registry ADD created_date  TIMESTAMP WITH TIME ZONE NULL;
UPDATE pipeline.dts_registry SET name = id::TEXT, created_date = now();
ALTER TABLE pipeline.dts_registry ALTER COLUMN name SET NOT NULL;
ALTER TABLE pipeline.dts_registry ALTER COLUMN created_date SET NOT NULL;
