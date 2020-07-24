ALTER TABLE pipeline.datastorage ADD s3_kms_key_arn TEXT NULL;
ALTER TABLE pipeline.datastorage ADD s3_use_assumed_creds boolean DEFAULT FALSE;
ALTER TABLE pipeline.datastorage ADD s3_temp_creds_role TEXT NULL;
