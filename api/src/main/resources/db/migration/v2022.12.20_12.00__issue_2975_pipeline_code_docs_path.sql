ALTER TABLE pipeline.pipeline ADD COLUMN code_path TEXT DEFAULT NULL;
ALTER TABLE pipeline.pipeline ADD COLUMN docs_path TEXT DEFAULT NULL;
UPDATE pipeline.pipeline SET code_path='src/', docs_path='docs/' WHERE repository_type=0;
UPDATE pipeline.pipeline SET code_path='/' WHERE repository_type=2;
