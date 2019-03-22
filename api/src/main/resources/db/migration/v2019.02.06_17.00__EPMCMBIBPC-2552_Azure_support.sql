ALTER TABLE pipeline.aws_region RENAME TO cloud_region;
ALTER TABLE pipeline.cloud_region RENAME COLUMN region_id TO region_name;
ALTER TABLE pipeline.cloud_region RENAME COLUMN aws_region_id TO region_id;
ALTER TABLE pipeline.cloud_region ADD cloud_provider text NULL;
ALTER TABLE pipeline.cloud_region ADD storage_account text NULL;
ALTER TABLE pipeline.cloud_region ADD storage_account_key text NULL;
UPDATE pipeline.cloud_region SET cloud_provider = 'AWS' WHERE cloud_provider ISNULL;
ALTER TABLE pipeline.cloud_region ALTER COLUMN cloud_provider SET NOT NULL;


ALTER TABLE pipeline.pipeline_run RENAME COLUMN node_aws_region TO node_cloud_region;
ALTER TABLE pipeline.pipeline_run ADD node_cloud_provider text;
UPDATE pipeline.pipeline_run SET node_cloud_provider = 'AWS';
ALTER TABLE pipeline.pipeline_run ALTER COLUMN node_cloud_provider SET NOT NULL;

UPDATE pipeline.pipeline_run SET node_cloud_region = regions.region_id::text
FROM (SELECT region_id FROM pipeline.cloud_region WHERE is_default = TRUE) AS regions;

ALTER TABLE pipeline.pipeline_run ALTER COLUMN node_cloud_region TYPE bigint USING node_cloud_region::bigint;
ALTER TABLE pipeline.pipeline_run ALTER COLUMN node_cloud_region SET NOT NULL;

DELETE FROM pipeline.instance_offer;
ALTER TABLE pipeline.instance_offer ALTER COLUMN region TYPE bigint USING region::bigint;

ALTER TABLE pipeline.cloud_region DROP CONSTRAINT aws_region_name_key;
ALTER TABLE pipeline.cloud_region DROP CONSTRAINT aws_region_region_id_key;