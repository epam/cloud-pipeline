CREATE SEQUENCE pipeline.s_file_share_mount START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS pipeline.file_share_mount(
    id BIGINT NOT NULL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    mount_root TEXT,
    mount_type TEXT,
    mount_options TEXT,
    CONSTRAINT  region_id_fkey  FOREIGN KEY (region_id) REFERENCES PIPELINE.CLOUD_REGION (region_id)
);

ALTER TABLE pipeline.datastorage ADD COLUMN FILE_SHARE_MOUNT_ID BIGINT;
ALTER TABLE pipeline.datastorage ADD CONSTRAINT file_share_mount_id_fk FOREIGN KEY (FILE_SHARE_MOUNT_ID) REFERENCES pipeline.file_share_mount(id);
ALTER TABLE pipeline.cloud_region DROP COLUMN EFS_HOSTS;

DO $$
DECLARE
    strg_region_id BIGINT;
    strg_id BIGINT;
    share_mount_id BIGINT;
    strg_path TEXT;
    strg_mount_options TEXT;
BEGIN
    SELECT region_id from cloud_region into strg_region_id where is_default;
    FOR strg_id, strg_path, strg_mount_options in
    	SELECT datastorage_id, path, mount_options FROM datastorage where datastorage_type = 'NFS'
    loop
    	SELECT nextval('s_file_share_mount') into share_mount_id;
        INSERT INTO file_share_mount(id, region_id, mount_root, mount_type, mount_options)
        VALUES (share_mount_id, strg_region_id, substring(strg_path from '([^:/]+)'), 'NFS', strg_mount_options);
        update datastorage set file_share_mount_id=share_mount_id where datastorage_id=strg_id;
    END LOOP;
END $$;
