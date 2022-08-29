-- Set DEFAULT as 1 because 1 is id of default admin
ALTER TABLE pipeline.datastorage_lifecycle_rule_prolongation ADD COLUMN user_id BIGINT DEFAULT 1 NOT NULL;
