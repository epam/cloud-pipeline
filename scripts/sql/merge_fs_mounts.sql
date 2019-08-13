DO $$
DECLARE
  shr_id BIGINT;
  shr_copy_id BIGINT;
  shr_path TEXT;
  shr_copy_path TEXT;
BEGIN
  FOR shr_id, shr_path in SELECT DISTINCT ON (mount_root) id, mount_root FROM pipeline.file_share_mount
  loop
    FOR shr_copy_id, shr_copy_path in SELECT id, mount_root FROM pipeline.file_share_mount where id != shr_id AND shr_path = mount_root
    loop
      UPDATE pipeline.datastorage SET file_share_mount_id = shr_id WHERE file_share_mount_id = shr_copy_id;
      DELETE FROM pipeline.file_share_mount WHERE id = shr_copy_id;
    END LOOP;
  END LOOP;
END $$;
