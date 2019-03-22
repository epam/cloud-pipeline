ALTER TABLE pipeline.cloud_region ADD temp_credentials_role text NULL;
ALTER TABLE pipeline.cloud_region ADD backup_duration INT NULL;
ALTER TABLE pipeline.cloud_region ADD versioning_enabled BOOLEAN NULL;

ALTER TABLE pipeline.cloud_region ADD ssh_public_key text NULL;
ALTER TABLE pipeline.cloud_region ADD meter_region_name text NULL;
ALTER TABLE pipeline.cloud_region ADD azure_api_url text NULL;
ALTER TABLE pipeline.cloud_region ADD price_offer_id text NULL;