ALTER TABLE pipeline.run_user ADD access_type text NULL;
UPDATE pipeline.run_user SET access_type = 'ENDPOINT' WHERE access_type ISNULL;