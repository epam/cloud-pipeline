ALTER TABLE pipeline.metadata_entity ADD created_date TIMESTAMP WITH TIME ZONE;
UPDATE pipeline.metadata_entity SET created_date = now();
ALTER TABLE pipeline.metadata_entity ALTER COLUMN created_date SET NOT NULL;
