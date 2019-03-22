ALTER TABLE pipeline.attachment ADD owner TEXT NULL;
UPDATE pipeline.attachment SET owner = 'Unauthorized';
ALTER TABLE pipeline.attachment ALTER COLUMN owner SET NOT NULL;